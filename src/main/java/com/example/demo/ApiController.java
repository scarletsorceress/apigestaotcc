package com.example.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {

    // -------------------- Repositórios --------------------
    @Autowired
    private TrabalhoRepository trabalhoRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired(required = false)
    private EmailService emailService;

    // -------------------- Uploads --------------------
    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        try {
            uploadPath = Paths.get(uploadDir);
            if (Files.notExists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório raiz de upload!", e);
        }
    }

    // ------------------------------------------------------
    // ------------------- TRABALHOS ------------------------
    // ------------------------------------------------------

    @PostMapping("/trabalhos")
    public ResponseEntity<Trabalho> criarTrabalho(@RequestBody CriarTrabalhoRequest request) {

        String id = UUID.randomUUID().toString();
        Trabalho novoTrabalho = new Trabalho(id, request.nome());

        trabalhoRepository.save(novoTrabalho);

        return ResponseEntity.status(HttpStatus.CREATED).body(novoTrabalho);
    }

    @GetMapping("/trabalhos")
    public ResponseEntity<Collection<Trabalho>> listarTrabalhos() {
        return ResponseEntity.ok(trabalhoRepository.findAll());
    }

    @GetMapping("/trabalhos/{trabalhoId}")
    public ResponseEntity<Trabalho> getTrabalho(@PathVariable String trabalhoId) {
        return trabalhoRepository.findById(trabalhoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------
    // ------------------- MENSAGENS ------------------------
    // ------------------------------------------------------

    @PostMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> receberMensagem(
            @PathVariable String trabalhoId,
            @RequestBody MessageRequest request
    ) {

        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado"));
        }

        Trabalho trabalho = optTrabalho.get();

        MessageRequest novaMensagem = new MessageRequest(
                request.getRemetente(),
                request.getTexto(),
                trabalho
        );

        trabalho.addMensagem(novaMensagem);
        trabalhoRepository.save(trabalho); // cascade salva mensagens

        try {
            if (emailService != null) {
                emailService.enviarNotificacao(request.getRemetente(), request.getTexto());
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "status", "Mensagem adicionada ao trabalho " + trabalhoId,
                            "mensagem", novaMensagem
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "Erro ao enviar e-mail", "erro", e.getMessage()));
        }
    }

    @GetMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> listarMensagens(@PathVariable String trabalhoId) {
        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado"));
        }
        return ResponseEntity.ok(optTrabalho.get().getMensagens());
    }

    // ------------------------------------------------------
    // ------------------- UPLOAD ---------------------------
    // ------------------------------------------------------

    @PostMapping("/trabalhos/{trabalhoId}/upload")
    public ResponseEntity<Map<String, String>> uploadDeArquivo(
            @PathVariable String trabalhoId,
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado"));
        }

        Trabalho trabalho = optTrabalho.get();

        if (arquivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Arquivo vazio"));
        }

        try {
            String filename = arquivo.getOriginalFilename();
            if (filename == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("erro", "Nome de arquivo inválido"));
            }

            Path jobUploadPath = uploadPath.resolve(trabalhoId);
            Files.createDirectories(jobUploadPath);

            Path destino = jobUploadPath.resolve(filename).normalize();

            Files.copy(arquivo.getInputStream(), destino);

            trabalho.addArquivo(filename);
            trabalhoRepository.save(trabalho);

            return ResponseEntity.ok(Map.of(
                    "status", "Upload realizado com sucesso",
                    "filename", filename
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao salvar o arquivo: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------
    // ------------------- DOWNLOAD -------------------------
    // ------------------------------------------------------

    @GetMapping("/trabalhos/{trabalhoId}/arquivos/{filename:.+}")
    public ResponseEntity<?> servirArquivo(
            @PathVariable String trabalhoId,
            @PathVariable String filename
    ) {

        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado"));
        }

        Trabalho trabalho = optTrabalho.get();

        if (!trabalho.getArquivos().contains(filename)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Arquivo não pertence a este trabalho"));
        }

        try {
            Path arquivoPath = uploadPath.resolve(trabalhoId).resolve(filename).normalize();
            Resource resource = new UrlResource(arquivoPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("erro", "Arquivo não encontrado no disco"));
            }

            String contentType = Files.probeContentType(arquivoPath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\""
                    )
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "URL malformada"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Erro ao ler o arquivo"));
        }
    }
}
