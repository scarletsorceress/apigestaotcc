package com.example.demo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Trabalho {

    @Id
    private String id;
    private String nome;

    @OneToMany(mappedBy = "trabalho", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MessageRequest> mensagens = new ArrayList<>();
    @ElementCollection(fetch = FetchType.LAZY)
    private List<String> arquivos = new ArrayList<>();

    public Trabalho() {
    }

    public Trabalho(String id, String nome) {
        this.id = id;
        this.nome = nome;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public List<MessageRequest> getMensagens() {
        return mensagens;
    }

    public void setMensagens(List<MessageRequest> mensagens) {
        this.mensagens = mensagens;
    }

    public List<String> getArquivos() {
        return arquivos;
    }

    public void setArquivos(List<String> arquivos) {
        this.arquivos = arquivos;
    }

    public void addMensagem(MessageRequest msg) {
        this.mensagens.add(msg);
        msg.setTrabalho(this);
    }

    public void addArquivo(String filename) {
        this.arquivos.add(filename);
    }
}