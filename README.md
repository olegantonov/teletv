<p align="center"><img src="docs/logo.png" width="128" alt="TeleTV"></p>

# TeleTV

Cliente não oficial do Telegram para TVs. Abre suas conversas, grupos e canais e toca os vídeos direto na tela grande, tudo pelo controle remoto. Feito para Fire TV e Android TV.

O app oficial do Telegram para Android roda em TV, mas em formato de celular e pensado para toque. O TeleTV existe para o caso de uso que sobra: sentar no sofá e assistir ao que foi postado nos grupos.

## O que faz

- **Conversas**: lista grupos, canais e conversas, com abas para as suas pastas e para os arquivados, e busca por nome.
- **Vídeos**: grade com capa, duração, tamanho e data. Alterna entre vídeos e arquivos de vídeo enviados como documento (MKV, MP4 etc.).
- **Filtros**: por data, tamanho do arquivo, duração, só baixados, busca por texto, e ordenação por data, tamanho ou duração.
- **Reprodução**: toca enquanto baixa e retoma de onde você parou.
- **Espaço controlado**: limite em GB para os downloads e prazo para apagar o que não é aberto há alguns dias. Os mais antigos saem primeiro.
- **Download automático**: escolha, conversa por conversa, quais devem ter os vídeos mais recentes baixados sozinhos (com o app aberto e respeitando o limite de espaço).
- **Senha**: PIN de 4 dígitos opcional para abrir o app.
- **Voz**: nos campos de busca vale o ditado do teclado da TV; em aparelhos com reconhecimento de voz para apps aparece o botão "Falar". No player, os comandos de voz do sistema (pausar, continuar, avançar) funcionam pela sessão de mídia.
- **Atualização**: o app procura novas versões nos releases deste repositório e instala por cima.

## Instalar

Baixe o APK mais recente em [Releases](../../releases/latest) e instale na TV.

- **Fire TV**: ative *Configurações → Minha Fire TV → Opções do desenvolvedor → Apps de fontes desconhecidas* e instale com o app Downloader ou com `adb install teletv-vX.Y.Z.apk`.
- **Android TV / Google TV**: envie o APK com um gerenciador de arquivos ou por `adb install`.

No primeiro uso, entre apontando o celular para o QR code (Telegram → Configurações → Dispositivos → Conectar dispositivo) ou digitando o número e o código.

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

O APK sai em `app/build/outputs/apk/debug/`. O build inclui a TDLib só para `armeabi-v7a`, a arquitetura da Fire TV Stick; aparelhos de 64 bits com suporte a apps de 32 bits também rodam.

Os releases são gerados pelo GitHub Actions ao criar uma tag `v*`, usando os secrets `TG_API_ID`, `TG_API_HASH`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.

## Privacidade

O TeleTV fala apenas com os servidores do Telegram e, para procurar atualizações, com a API do GitHub. Não tem telemetria nem servidor próprio. A sessão e os vídeos baixados ficam no armazenamento interno do app.

A senha do app protege a interface contra quem pega o controle remoto; não é criptografia dos dados.

## Aviso

Este projeto não é afiliado ao Telegram nem endossado por ele. O uso está sujeito aos [termos da API do Telegram](https://core.telegram.org/api/terms).

## Licença

[MIT](LICENSE). Componentes de terceiros e suas licenças estão em [LICENCAS-TERCEIROS.md](LICENCAS-TERCEIROS.md).
