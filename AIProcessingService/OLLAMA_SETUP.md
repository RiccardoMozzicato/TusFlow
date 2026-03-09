# Configurazione Ollama per Embeddings Locali

## Installazione Ollama

### macOS
```bash
brew install ollama
```

### Linux
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows
Scarica l'installer da: https://ollama.com/download

## Avvio Ollama

```bash
# Avvia il server Ollama (default porta 11434)
ollama serve
```

## Download del modello per embeddings

```bash
# Scarica il modello nomic-embed-text (768 dimensioni)
ollama pull nomic-embed-text
```

## Verifica installazione

```bash
# Controlla che il modello sia stato scaricato
ollama list

# Test embedding
curl http://localhost:11434/api/embeddings -d '{
  "model": "nomic-embed-text",
  "prompt": "Hello world"
}'
```

## Configurazione Spring AI

Nel file `application.yml` è già configurato:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text
    vectorstore:
      pgvector:
        dimensions: 768  # nomic-embed-text usa 768 dimensioni
```

## Variabili d'ambiente (opzionali)

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

## Modelli alternativi

Se vuoi usare un modello diverso:

### all-minilm (384 dimensioni - più veloce, meno accurato)
```bash
ollama pull all-minilm
```

Poi aggiorna `application.yml`:
```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 384
```

### mxbai-embed-large (1024 dimensioni - più accurato)
```bash
ollama pull mxbai-embed-large
```

Poi aggiorna `application.yml`:
```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 1024
```

## Vantaggi di Ollama

✅ **Gratuito**: Nessun costo API  
✅ **Locale**: Privacy completa, nessun dato inviato a terzi  
✅ **Veloce**: Nessuna latency di rete  
✅ **Offline**: Funziona senza connessione internet  
✅ **Semplice**: Setup in 2 minuti  

## Troubleshooting

### Ollama non risponde
```bash
# Controlla se il processo è attivo
ps aux | grep ollama

# Riavvia il servizio
pkill ollama
ollama serve
```

### Porta già in uso
Se la porta 11434 è occupata, puoi cambiare porta:
```bash
OLLAMA_HOST=0.0.0.0:11435 ollama serve
```

E aggiorna `application.yml`:
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11435
```

