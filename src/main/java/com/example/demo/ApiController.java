package com.example.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired(required = false)
    private EmailService emailService;

    // Cliente HTTP para microserviço
    private final WebClient trabalhoClient = WebClient.create("http://localhost:8081");

    // Upload
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
            throw new RuntimeException("Não foi possível criar diretório de upload!", e);
        }
    }

    // -----------------------------------------------------------------------
    // ------------------------ TRABALHOS (MICROSERVIÇO) ----------------------
    // -----------------------------------------------------------------------

    @PostMapping("/trabalhos")
    public ResponseEntity<TrabalhoDTO> criarTrabalho(@RequestBody CriarTrabalhoRequest request) {

        TrabalhoDTO response = trabalhoClient.post()
                .uri("/trabalhos")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TrabalhoDTO.class)
                .block();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/trabalhos")
    public ResponseEntity<Collection<TrabalhoDTO>> listarTrabalhos() {

        Collection<TrabalhoDTO> trabalhos = trabalhoClient.get()
                .uri("/trabalhos")
                .retrieve()
                .bodyToFlux(TrabalhoDTO.class)
                .collectList()
                .block();

        return ResponseEntity.ok(trabalhos);
    }

    @GetMapping("/trabalhos/{trabalhoId}")
    public ResponseEntity<TrabalhoDTO> getTrabalho(@PathVariable String trabalhoId) {

        TrabalhoDTO trabalho = trabalhoClient.get()
                .uri("/trabalhos/" + trabalhoId)
                .retrieve()
                .bodyToMono(TrabalhoDTO.class)
                .block();

        return ResponseEntity.ok(trabalho);
    }

    // -----------------------------------------------------------------------
    // ------------------------ MENSAGENS ------------------------------------
    // -----------------------------------------------------------------------

    @PostMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> receberMensagem(
            @PathVariable String trabalhoId,
            @RequestBody MessageRequest request
    ) {

        // 1. Verificar se o trabalho existe no microserviço
        try {
            trabalhoClient.get()
                    .uri("/trabalhos/" + trabalhoId)
                    .retrieve()
                    .bodyToMono(TrabalhoDTO.class)
                    .block();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado no microserviço"));
        }

        // 2. Criar mensagem local (apenas ID de trabalho)
        MessageRequest novaMensagem = new MessageRequest(
                request.getRemetente(),
                request.getTexto(),
                trabalhoId
        );

        messageRepository.save(novaMensagem);

        // 3. Enviar e-mail opcionalmente
        if (emailService != null) {
            try {
                emailService.enviarNotificacao(request.getRemetente(), request.getTexto());
            } catch (Exception ignored) {}
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "status", "Mensagem adicionada",
                        "mensagem", novaMensagem
                ));
    }

    @GetMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> listarMensagens(@PathVariable String trabalhoId) {

        // Garantir que o trabalho existe
        try {
            trabalhoClient.get()
                    .uri("/trabalhos/" + trabalhoId)
                    .retrieve()
                    .bodyToMono(TrabalhoDTO.class)
                    .block();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado"));
        }

        List<MessageRequest> mensagens = messageRepository.findByTrabalhoId(trabalhoId);

        return ResponseEntity.ok(mensagens);
    }

    // -----------------------------------------------------------------------
    // ------------------------ UPLOAD ---------------------------------------
    // -----------------------------------------------------------------------

    @PostMapping("/trabalhos/{trabalhoId}/upload")
    public ResponseEntity<Map<String, String>> uploadDeArquivo(
            @PathVariable String trabalhoId,
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        // Verificar existência do trabalho no microserviço
        try {
            trabalhoClient.get()
                    .uri("/trabalhos/" + trabalhoId)
                    .retrieve()
                    .bodyToMono(TrabalhoDTO.class)
                    .block();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado no microserviço"));
        }

        if (arquivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Arquivo vazio"));
        }

        try {
            String filename = arquivo.getOriginalFilename();
            Path jobUploadPath = uploadPath.resolve(trabalhoId);
            Files.createDirectories(jobUploadPath);

            Path destino = jobUploadPath.resolve(filename).normalize();
            Files.copy(arquivo.getInputStream(), destino);

            return ResponseEntity.ok(Map.of(
                    "status", "Upload realizado",
                    "filename", filename
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao salvar arquivo"));
        }
    }

    // -----------------------------------------------------------------------
    // ------------------------ DOWNLOAD -------------------------------------
    // -----------------------------------------------------------------------

    @GetMapping("/trabalhos/{trabalhoId}/arquivos/{filename:.+}")
    public ResponseEntity<?> servirArquivo(
            @PathVariable String trabalhoId,
            @PathVariable String filename
    ) {

        // Verificar existência do trabalho no microserviço
        try {
            trabalhoClient.get()
                    .uri("/trabalhos/" + trabalhoId)
                    .retrieve()
                    .bodyToMono(TrabalhoDTO.class)
                    .block();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Trabalho não encontrado no microserviço"));
        }

        try {
            Path arquivoPath = uploadPath.resolve(trabalhoId).resolve(filename).normalize();
            Resource resource = new UrlResource(arquivoPath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("erro", "Arquivo não existe"));
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "URL inválida"));
        }
    }
}
