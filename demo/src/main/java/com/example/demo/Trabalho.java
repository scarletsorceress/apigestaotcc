package com.example.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Trabalho {

    private String id;
    private String nome;

    // Usamos listas sincronizadas para segurança em ambiente web (thread-safety)
    private List<Message> mensagens = Collections.synchronizedList(new ArrayList<>());
    private List<String> arquivos = Collections.synchronizedList(new ArrayList<>());

    // Construtor
    public Trabalho(String id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    // Getters (e Setters se precisar, mas vamos focar em getters)
    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public List<Message> getMensagens() {
        return mensagens;
    }

    public List<String> getArquivos() {
        return arquivos;
    }

    // Métodos úteis para adicionar itens
    public void addMensagem(Message msg) {
        this.mensagens.add(msg);
    }

    public void addArquivo(String filename) {
        this.arquivos.add(filename);
    }
}