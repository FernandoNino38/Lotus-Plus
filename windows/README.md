# Lotus — Windows Edition (C# / .NET 8 WPF)

Versão nativa para Windows do reprodutor de músicas **Lotus**, mantendo o aplicativo Android original intacto na pasta `app/`.

## Funcionalidades

- **Reprodução de Áudio Multi-formato (`NAudio` + `NAudio.Vorbis`)**: Suporta `MP3`, `FLAC`, `OGG`, `WAV`, `M4A`, `AAC`, `WMA`, `OPUS`, `AIFF` e mais, com selo de qualidade de áudio (`LOSSLESS`, bitrate em kbps e taxa de amostragem em kHz).
- **Biblioteca Completa**:
  - Navegação por **Faixas**, **Playlists**, **Álbuns**, **Artistas**, **Gêneros** e **Pastas**.
  - **Playlists Automáticas**: *Adicionadas Recentemente* e *Mais Tocadas*.
  - **Playlists Personalizadas**: Criação, renomeação, exclusão, reordenação de faixas e importação/exportação de arquivos `.M3U` / `.M3U8`.
  - **Busca, Filtro e Ordenação**: Ordenação por Título, Álbum, Artista, Gênero, Ano, Nº da Faixa ou Data de Modificação (crescente/decrescente).
- **Letras Sincronizadas & Integração com LRCLIB (`https://lrclib.net`)**:
  - Leitura automática de letras embutidas nas tags do arquivo, arquivos `.lrc` locais ou busca online no **LRCLIB**.
  - Suporte a LRC padrão (`[mm:ss.xx]`), LRC estendido palavra por palavra (`<mm:ss.xx>`) e formato Kugou, além de detecção automática de interlúdios instrumentais (`•••`).
  - Modo **Sincronizada** (com rolagem automática e clique em qualquer verso para avançar a música) e modo **Texto Puro**, além de **Modo Tela Cheia Imersivo (`F11`)** e ajuste fino de offset em milissegundos.
  - Publicação de letras no LRCLIB com resolução de desafio criptográfico SHA-256 Proof-of-Work.
- **Editor de Metadados (Tags) & MusicBrainz**:
  - Edição manual de Título, Artista, Álbum, Artista do Álbum, Gênero, Ano, Nº da Faixa, Capa do Álbum e Letra embutida usando `TagLibSharp`.
  - Busca automática de metadados no **MusicBrainz** e download automático de capa no **Cover Art Archive**.
- **Tema Dinâmico Material You**:
  - Extração automática da cor dominante/vibrante da capa do álbum em reprodução, com suporte aos estilos `TonalSpot`, `Vibrant`, `Expressive`, `Neutral` e `Monochrome`, além de temas Claro, Escuro e AMOLED.
- **Temporizador de Sono (Sleep Timer)** e **Fila de Reprodução**.

## Como Executar

1. **Executar diretamente o executável compilado**:
   - Dê dois cliques em `run-windows.bat` na raiz do projeto, ou execute diretamente:
     ```powershell
     .\windows\publish\Lotus.exe
     ```

2. **Compilar e rodar via .NET CLI**:
   ```powershell
   dotnet run --project windows\Lotus.Windows\Lotus.Windows.csproj -c Release
   ```

3. **Gerar novo executável em `windows\publish`**:
   ```powershell
   dotnet publish windows\Lotus.Windows\Lotus.Windows.csproj -c Release -r win-x64 --self-contained false -o windows\publish
   ```
