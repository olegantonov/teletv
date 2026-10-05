<p align="center"><img src="docs/logo.png" width="128" alt="TeleTV"></p>

# TeleTV

[English](README.md) · **Português**

Cliente não oficial do Telegram para TVs. Abre suas conversas, grupos e canais e toca os vídeos direto na tela grande, tudo pelo controle remoto. Feito para Fire TV e Android TV.

O app oficial do Telegram para Android roda em TV, mas em formato de celular e pensado para toque. O TeleTV existe para o caso de uso que sobra: sentar no sofá e assistir ao que foi postado nos grupos. É um visualizador: não envia mensagens.

## O que faz

### Tela inicial

- **Continuar assistindo**: uma fileira no topo com os vídeos que você deixou pela metade, cada um com barra de progresso.
- **Conversas por aba**: Todos, Favoritos, uma aba para cada pasta do Telegram e Arquivados. Busca de conversas por nome.
- **Biblioteca**: histórico, vídeos em andamento e vídeos favoritos num lugar só.
- **Menu da conversa** (segure OK): adicionar aos favoritos, ligar ou desligar o download automático.

### Dentro de uma conversa

- **Grade de vídeos** com capa, duração, tamanho, data, estado do download e quanto você já assistiu.
- **Filtros**: data, tamanho do arquivo, duração, só baixados, busca por texto e ordenação por data, tamanho ou duração. Com filtro ligado, o app segue buscando mensagens antigas até encher a tela.
- **Vídeos ou arquivos de vídeo**: alterne para arquivos enviados como documento (MKV, MP4 etc.).
- **Menu do vídeo** (segure OK): favoritar, apagar o download, remover do histórico.

### Player

- **Streaming**: toca enquanto o arquivo ainda baixa e retoma de onde você parou.
- **Esquerda / direita** pulam 10 segundos; segurar a tecla acelera.
- **Menu** alterna o zoom; **OK** abre os controles com legendas, faixa de áudio e velocidade.
- **Voltar** uma vez esconde os controles; **Voltar** duas vezes sai do vídeo.

### Espaço e downloads

- **Limite de tamanho** para os vídeos baixados (0,5 a 8 GB) e **limite de tempo** para vídeos que você não abriu. Os mais antigos saem primeiro, e o espaço é liberado antes de cada vídeo novo começar.
- **Download automático**: escolha, conversa por conversa, quais grupos e canais devem ter os vídeos mais recentes baixados sozinhos. Só roda com o app aberto e nunca passa do limite de tamanho.

### Configuração e ajustes

- **Guia de primeira execução**: login, armazenamento, downloads automáticos, senha e atalhos do controle, em sete passos curtos.
- **Três formas de entrar**: apontar o app do Telegram para um QR code, digitar pelo controle, ou ler um segundo QR code e digitar tudo no celular.
- **Sua própria chave de API do Telegram** (opcional): a página do celular guia a criação em my.telegram.org.
- **Senha**: PIN de 4 dígitos opcional, pedido sempre que o app abre.
- **Atualização**: o app consulta os releases deste repositório e instala as novas versões por cima.
- **Idiomas**: inglês e português, conforme o idioma do aparelho.
- **Voz**: os campos de busca aceitam o ditado do teclado da TV; em aparelhos com reconhecimento de voz para apps aparece o botão "Falar"; o player responde aos comandos de voz de mídia do sistema.

## Instalar

Baixe o APK mais recente em [Releases](../../releases/latest) e instale na TV.

- **Fire TV**: ative *Configurações → Minha Fire TV → Opções do desenvolvedor → Apps de fontes desconhecidas* e instale com o app Downloader (informe o endereço do APK na página do release) ou com `adb install teletv-vX.Y.Z.apk`.
- **Android TV / Google TV**: envie o APK com um gerenciador de arquivos ou por `adb install`.
- **Obtainium**: adicione o endereço deste repositório ao [Obtainium](https://github.com/ImranR98/Obtainium) e ele acompanha os novos releases.

Para o app conseguir se atualizar sozinho, autorize-o a instalar apps quando ele pedir (na Fire TV: *Opções do desenvolvedor → Instalar apps desconhecidos → TeleTV*).

## Compilar

Requisitos: JDK 17 e Android SDK (plataforma 34).

1. Crie suas credenciais em <https://my.telegram.org> → *API development tools*.
2. Coloque-as no `local.properties`, que não vai para o git:

   ```properties
   sdk.dir=/caminho/para/android-sdk
   tg.apiId=123456
   tg.apiHash=0123456789abcdef0123456789abcdef
   ```

3. Compile:

   ```bash
   ./gradlew assembleDebug
   ```

O APK sai em `app/build/outputs/apk/debug/`. Um build sem credenciais também funciona: na primeira abertura ele pede a chave de API pela página de configuração do celular.

O build inclui a TDLib só para `armeabi-v7a`, a arquitetura da Fire TV Stick; aparelhos de 64 bits com suporte a apps de 32 bits também rodam.

Os releases são gerados pelo GitHub Actions ao criar uma tag `v*`, usando os secrets `TG_API_ID`, `TG_API_HASH`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.

## Como é feito

Kotlin com views comuns do Android, sem framework de interface. A [TDLib](https://github.com/tdlib/td) cuida do protocolo do Telegram e dos downloads; o [Media3 ExoPlayer](https://github.com/androidx/media) toca o arquivo enquanto a TDLib ainda o baixa, por meio de uma fonte de dados pequena que pede à TDLib o trecho de bytes de que o player precisa. Histórico, favoritos e ajustes ficam guardados no aparelho.

## Contribuir

Issues e pull requests são bem-vindos. As traduções ficam em `app/src/main/res/values-*/strings.xml`: copie `values/strings.xml` para uma pasta `values-<idioma>` e traduza.

## Privacidade

O TeleTV fala apenas com os servidores do Telegram e, para procurar atualizações, com a API do GitHub. Não tem telemetria nem servidor próprio. A sessão, os vídeos baixados, o histórico e os favoritos ficam no armazenamento interno do app.

A senha do app protege a interface contra quem pega o controle remoto; não é criptografia dos dados.

A página de configuração pelo celular é HTTP simples dentro da sua rede local. Ela só fica no ar enquanto a tela de login está aberta e o endereço leva um código aleatório, mas evite usá-la em redes em que você não confia.

## Apoie

O TeleTV é gratuito, de código aberto e sem anúncios. Se ele é útil para você, uma doação em Bitcoin ajuda a manter o projeto e o desenvolvedor:

```
14XJqVsMfVLpm6s4mX7mHNooihvtdfJq5J
```

No app, o QR code fica em *Configurações → Apoiar o projeto*.

## Aviso

Este projeto não é afiliado ao Telegram nem endossado por ele. O uso está sujeito aos [termos da API do Telegram](https://core.telegram.org/api/terms).

## Licença

[MIT](LICENSE). Componentes de terceiros e suas licenças estão em [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).
