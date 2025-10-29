package com.example.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

import jakarta.annotation.PostConstruct;

@RestController // Marca esta classe como um controller REST (converte retornos para JSON)
@RequestMapping("/api") // Prefixo para todas as rotas nesta classe (ex: /api/mensagens)
public class ApiController {

    @Autowired
    private EmailService emailService;

    // ... (dentro da classe ApiController)
    // --- Endpoint 3: Baixar/Servir Arquivos ---
    @GetMapping("/arquivos/{filename:.+}")
    public ResponseEntity<Resource> servirArquivo(@PathVariable String filename) {

        try {
            // 1. Resolve o caminho completo do arquivo
            Path arquivoPath = this.uploadPath.resolve(filename).normalize();

            // 2. Cria um 'Resource' do Spring a partir do caminho do arquivo
            Resource resource = new UrlResource(arquivoPath.toUri());

            // 3. Verifica se o arquivo existe e pode ser lido
            if (!resource.exists() || !resource.isReadable()) {
                // Se não existir, retorna um erro 404 (Not Found)
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null); // Ou um JSON de erro
            }

            // --- Verificação de Segurança (Importante!) ---
            // Impede que tentem acessar arquivos fora da pasta de upload (ex: "..\..\windows\system.ini")
            if (!arquivoPath.startsWith(this.uploadPath.normalize())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(null); // Erro 403 (Forbidden)
            }

            // 4. Tenta descobrir o tipo do arquivo (MIME Type)
            String contentType = Files.probeContentType(arquivoPath);
            if (contentType == null) {
                // Se não conseguir descobrir, usa um tipo genérico
                contentType = "application/octet-stream";
            }

            // 5. Constrói a resposta final
            return ResponseEntity.ok()
                    // Define o tipo de conteúdo (ex: 'image/jpeg', 'application/pdf')
                    .contentType(MediaType.parseMediaType(contentType))
                    // --- Este é o cabeçalho que "força" o download ---
                    // Ele diz ao navegador: "Trate isso como um anexo com este nome"
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    // Envia o arquivo no corpo da resposta
                    .body(resource);

        } catch (MalformedURLException e) {
            // Erro ao criar a URL para o Resource
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        } catch (IOException e) {
            // Erro ao tentar ler o tipo de conteúdo
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    // "Banco de dados" em memória para as mensagens
    private final List<Message> mensagensDb = new ArrayList<>();

    // Injeta o valor da propriedade 'file.upload-dir' do application.properties
    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadPath;

    // Este método roda uma vez quando o Spring inicia
    @PostConstruct
    public void init() {
        try {
            uploadPath = Paths.get(uploadDir);
            if (Files.notExists(uploadPath)) {
                Files.createDirectories(uploadPath); // Cria a pasta 'uploads' se não existir
            }
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório de upload!", e);
        }
    }

    // --- Endpoint 1: Mensagens ---
    @PostMapping("/mensagens")
    public ResponseEntity<?> receberMessage(@RequestBody Message message) {
        mensagensDb.add(message);

        try {
            // Tenta enviar e-mail
            emailService.enviarNotificacao(message.remetente(), message.texto());

            // Retorna status e mensagem de sucesso
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "status", "Mensagem enviada com sucesso!",
                            "remetente", message.remetente(),
                            "texto", message.texto()
                    ));

        } catch (Exception e) {
            // Loga o erro no console para você ver o problema
            e.printStackTrace();

            // Retorna erro com mensagem
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "Erro ao enviar a mensagem",
                            "erro", e.getMessage()
                    ));
        }
    }

    @GetMapping("/mensagens") // Mapeia requisições GET para /api/mensagens
    public ResponseEntity<List<Message>> listarMensagens() {
        // Retorna a lista completa de mensagens com status 200 (OK)
        return ResponseEntity.ok(mensagensDb);
    }

    // --- Endpoint 2: Upload de Arquivo ---
    @PostMapping("/upload") // Mapeia requisições POST para /api/upload
    public ResponseEntity<Map<String, String>> uploadDeArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        // @RequestParam("arquivo") busca pelo arquivo enviado no form-data com a chave "arquivo"

        if (arquivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Arquivo vazio"));
        }

        try {
            // Pega o nome original do arquivo
            String filename = arquivo.getOriginalFilename();
            if (filename == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Nome de arquivo inválido"));
            }

            // Resolve o caminho completo (ex: ./uploads/meu-documento.pdf)
            Path destino = this.uploadPath.resolve(filename);

            // Copia o stream do arquivo para o local de destino
            Files.copy(arquivo.getInputStream(), destino);

            // Retorna uma resposta de sucesso
            return ResponseEntity.ok(Map.of(
                    "status", "Upload com sucesso",
                    "filename", filename
            ));

        } catch (IOException e) {
            // Retorna um erro caso falhe ao salvar
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao salvar o arquivo: " + e.getMessage()));
        }
    }
}
