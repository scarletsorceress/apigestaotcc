package com.example.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection; // Importado
import java.util.List;
import java.util.Map;
import java.util.UUID; // Importado
import java.util.concurrent.ConcurrentHashMap; // Importado

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

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired(required = false) // Coloquei (required = false) para o caso de você não ter o EmailService configurado
    private EmailService emailService;

    // --- NOSSO NOVO "BANCO DE DADOS" ---
    // Trocamos a List<Message> por um Map<String, Trabalho>
    // A chave (String) será o ID do trabalho.
    private final Map<String, Trabalho> trabalhosDb = new ConcurrentHashMap<>();

    // Injeta o valor da propriedade 'file.upload-dir' do application.properties
    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadPath; // Este agora é o diretório *raiz* (ex: ./uploads)

    // Este método roda uma vez quando o Spring inicia
    @PostConstruct
    public void init() {
        try {
            // Apenas garante que a pasta *raiz* de uploads exista
            uploadPath = Paths.get(uploadDir);
            if (Files.notExists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório raiz de upload!", e);
        }
    }

    // --- Endpoint 1: Gerenciamento de Trabalhos ---

    @PostMapping("/trabalhos")
    public ResponseEntity<Trabalho> criarTrabalho(@RequestBody CriarTrabalhoRequest request) {
        // Gera um ID único
        String id = UUID.randomUUID().toString();
        Trabalho novoTrabalho = new Trabalho(id, request.nome());

        trabalhosDb.put(id, novoTrabalho);

        return ResponseEntity.status(HttpStatus.CREATED).body(novoTrabalho);
    }

    @GetMapping("/trabalhos")
    public ResponseEntity<Collection<Trabalho>> listarTrabalhos() {
        return ResponseEntity.ok(trabalhosDb.values());
    }

    @GetMapping("/trabalhos/{trabalhoId}")
    public ResponseEntity<Trabalho> getTrabalho(@PathVariable String trabalhoId) {
        Trabalho trabalho = trabalhosDb.get(trabalhoId);
        if (trabalho == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(trabalho);
    }

    // --- Endpoint 2: Mensagens (Agora LIGADAS a um Trabalho) ---

    @PostMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> receberMensagem(@PathVariable String trabalhoId, @RequestBody Message message) {
        // 1. Encontra o trabalho
        Trabalho trabalho = trabalhosDb.get(trabalhoId);
        if (trabalho == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }

        // 2. Adiciona a mensagem ao trabalho
        trabalho.addMensagem(message);

        // 3. Tenta enviar o e-mail (se o serviço estiver configurado)
        try {
            if (emailService != null) {
                emailService.enviarNotificacao(message.remetente(), message.texto());
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "status", "Mensagem adicionada ao trabalho " + trabalhoId,
                            "mensagem", message
                    ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "Erro ao enviar e-mail", "erro", e.getMessage()));
        }
    }

    @GetMapping("/trabalhos/{trabalhoId}/mensagens")
    public ResponseEntity<?> listarMensagens(@PathVariable String trabalhoId) {
        Trabalho trabalho = trabalhosDb.get(trabalhoId);
        if (trabalho == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }
        // Retorna apenas as mensagens *desse* trabalho
        return ResponseEntity.ok(trabalho.getMensagens());
    }

    // --- Endpoint 3: Upload de Arquivo (Agora LIGADO a um Trabalho) ---

    @PostMapping("/trabalhos/{trabalhoId}/upload")
    public ResponseEntity<Map<String, String>> uploadDeArquivo(@PathVariable String trabalhoId, @RequestParam("arquivo") MultipartFile arquivo) {

        // 1. Encontra o trabalho
        Trabalho trabalho = trabalhosDb.get(trabalhoId);
        if (trabalho == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }

        if (arquivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Arquivo vazio"));
        }

        try {
            String filename = arquivo.getOriginalFilename();
            if (filename == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Nome de arquivo inválido"));
            }

            // --- MUDANÇA IMPORTANTE: Salva em uma subpasta com o ID do trabalho ---
            // 2. Cria o caminho da subpasta (ex: ./uploads/{trabalhoId}/)
            Path jobUploadPath = this.uploadPath.resolve(trabalhoId);
            Files.createDirectories(jobUploadPath); // Cria a subpasta se não existir

            // 3. Resolve o destino final do arquivo (ex: ./uploads/{trabalhoId}/meu-arquivo.txt)
            Path destino = jobUploadPath.resolve(filename).normalize();

            // 4. Copia o arquivo
            Files.copy(arquivo.getInputStream(), destino);

            // 5. Adiciona o nome do arquivo à lista do trabalho
            trabalho.addArquivo(filename);

            return ResponseEntity.ok(Map.of(
                    "status", "Upload com sucesso para o trabalho " + trabalhoId,
                    "filename", filename
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao salvar o arquivo: " + e.getMessage()));
        }
    }

    // --- Endpoint 4: Download de Arquivo (Agora LIGADO a um Trabalho) ---

    @GetMapping("/trabalhos/{trabalhoId}/arquivos/{filename:.+}")
    public ResponseEntity<?> servirArquivo(@PathVariable String trabalhoId, @PathVariable String filename) {

        // 1. Encontra o trabalho
        Trabalho trabalho = trabalhosDb.get(trabalhoId);
        if (trabalho == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Trabalho não encontrado"));
        }

        // 2. Verifica se o trabalho *realmente* possui esse arquivo (Segurança)
        if (!trabalho.getArquivos().contains(filename)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Arquivo não pertence a este trabalho"));
        }

        try {
            // 3. Resolve o caminho do arquivo DENTRO da subpasta do trabalho
            Path arquivoPath = this.uploadPath.resolve(trabalhoId).resolve(filename).normalize();

            Resource resource = new UrlResource(arquivoPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Arquivo não encontrado no disco"));
            }

            // A verificação de segurança (path traversal) agora é implicitamente tratada
            // porque buscamos o arquivo dentro da pasta do trabalhoId

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