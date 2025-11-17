package com.example.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional; // Importe o Optional
import java.util.UUID;

// Imports do Spring
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

    // --- MUDANÇA: Injeção dos Repositórios ---
    // Removemos o Map!
    @Autowired
    private TrabalhoRepository trabalhoRepository;

    @Autowired
    private MessageRepository messageRepository; // JPA gerencia as mensagens

    @Autowired(required = false)
    private EmailService emailService;

    // --- Esta parte (Uploads) continua igual ---
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

    // --- Endpoint 1: Gerenciamento de Trabalhos (Atualizado) ---

    @PostMapping("/trabalhos")
    public ResponseEntity<Trabalho> criarTrabalho(@RequestBody CriarTrabalhoRequest request) {
        String id = UUID.randomUUID().toString();
        Trabalho novoTrabalho = new Trabalho(id, request.nome());

        // --- MUDANÇA: Salva no banco de dados ---
        trabalhoRepository.save(novoTrabalho);

        return ResponseEntity.status(HttpStatus.CREATED).body(novoTrabalho);
    }

    @GetMapping("/trabalhos")
    public ResponseEntity<Collection<Trabalho>> listarTrabalhos() {
        // --- MUDANÇA: Busca todos do banco ---
        return ResponseEntity.ok(trabalhoRepository.findAll());
    }

    @GetMapping("/trabalhos/{trabalhoId}")
    public ResponseEntity<Trabalho> getTrabalho(@PathVariable String trabalhoId) {
        // --- MUDANÇA: Busca por ID usando Optional ---
        return trabalhoRepository.findById(trabalhoId)
                .map(ResponseEntity::ok) // Se encontrar, retorna 200 OK com o trabalho
                .orElse(ResponseEntity.notFound().build()); // Se não, retorna 404
    }

    // --- Endpoint 2: Mensagens (Atualizado) ---

    @PostMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> receberMensagem(@PathVariable String trabalhoId, @RequestBody MessageRequest request) {

        // 1. Encontra o trabalho (retorna Optional)
        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }
        Trabalho trabalho = optTrabalho.get();

        // 2. Cria a nova entidade Message (usando o DTO 'MessageRequest')
        MessageRequest novaMensagem = new MessageRequest(request.getRemetente(), request.getTexto(), trabalho);

        // 3. Adiciona a mensagem ao trabalho (Isso já linka os dois lados via @ManyToOne)
        trabalho.addMensagem(novaMensagem);

        // 4. Salva o 'Trabalho'. Por causa do 'CascadeType.ALL' na Entidade Trabalho,
        // o Spring salvará a 'novaMensagem' automaticamente.
        trabalhoRepository.save(trabalho);

        // 5. Tenta enviar o e-mail
        try {
            if (emailService != null) {
                emailService.enviarNotificacao(request.getRemetente(), request.getTexto());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "Mensagem adicionada ao trabalho " + trabalhoId,
                    "mensagem", novaMensagem // Retorna a mensagem criada
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "Erro ao enviar e-mail", "erro", e.getMessage()));
        }
    }

    @GetMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> listarMensagens(@PathVariable String trabalhoId) {
        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }
        // Retorna apenas as mensagens *desse* trabalho
        return ResponseEntity.ok(optTrabalho.get().getMensagens());
    }

    // --- Endpoint 3: Upload de Arquivo (Atualizado) ---

    @PostMapping("/trabalhos/{trabalhoId}/upload")
    public ResponseEntity<Map<String, String>> uploadDeArquivo(@PathVariable String trabalhoId, @RequestParam("arquivo") MultipartFile arquivo) {

        // 1. Busca o trabalho no banco
        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }
        Trabalho trabalho = optTrabalho.get();

        if (arquivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Arquivo vazio"));
        }

        try {
            String filename = arquivo.getOriginalFilename();
            if (filename == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Nome de arquivo inválido"));
            }

            // O resto da sua lógica de salvar o arquivo está perfeita
            Path jobUploadPath = this.uploadPath.resolve(trabalhoId);
            Files.createDirectories(jobUploadPath);
            Path destino = jobUploadPath.resolve(filename).normalize();

            Files.copy(arquivo.getInputStream(), destino);

            // --- MUDANÇA: Adiciona o nome do arquivo à entidade ---
            trabalho.addArquivo(filename);

            // --- MUDANÇA: Salva a entidade 'Trabalho' atualizada no banco ---
            trabalhoRepository.save(trabalho);

            return ResponseEntity.ok(Map.of(
                    "status", "Upload com sucesso para o trabalho " + trabalhoId,
                    "filename", filename
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao salvar o arquivo: " + e.getMessage()));
        }
    }

    // --- Endpoint 4: Download de Arquivo (Atualizado) ---

    @GetMapping("/trabalhos/{trabalhoId}/arquivos/{filename:.+}")
    public ResponseEntity<?> servirArquivo(@PathVariable String trabalhoId, @PathVariable String filename) {

        // 1. Encontra o trabalho (ou retorna 404)
        Optional<Trabalho> optTrabalho = trabalhoRepository.findById(trabalhoId);
        if (optTrabalho.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }
        Trabalho trabalho = optTrabalho.get();

        // 2. Verifica se o trabalho *realmente* possui esse arquivo (Segurança)
        if (!trabalho.getArquivos().contains(filename)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Arquivo não pertence a este trabalho"));
        }

        // 3. O resto do seu código de servir o arquivo está PERFEITO e não muda!
        try {
            Path arquivoPath = this.uploadPath.resolve(trabalhoId).resolve(filename).normalize();
            Resource resource = new UrlResource(arquivoPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Arquivo não encontrado no disco"));
            }

            String contentType = Files.probeContentType(arquivoPath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "URL malformada"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "Erro ao ler o arquivo"));
        }
    }
}