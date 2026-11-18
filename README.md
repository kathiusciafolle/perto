# Perto

Chat que funciona **sem internet**, de celular para celular, usando **Bluetooth**.
As mensagens pulam de um aparelho para outro até chegar em quem está mais longe.

**[Site do Perto](https://kathiusciafolle.github.io/perto/)** ·
**[Baixar o APK](https://github.com/kathiusciafolle/perto/releases/latest/download/Perto.apk)** ·
[Todas as versões](https://github.com/kathiusciafolle/perto/releases)

O Perto é baseado no [bitchat para Android](https://github.com/permissionlesstech/bitchat-android).
A rede Bluetooth, a criptografia e o envio das mensagens são os do bitchat. O Perto troca a
interface por uma mais familiar, com cara de app de mensagens, e acrescenta alguns recursos.

## O que o Perto tem de diferente

- **Abas Conversas, Perto e Mural**
  - Conversas: lista de conversas privadas, com não lidas e amigos fixos no topo.
  - Perto: radar de quem está no alcance do Bluetooth.
  - Mural: canal aberto para todo mundo por perto.
- **Achar ("quente ou frio")**: pela força do sinal Bluetooth, o celular vibra mais rápido
  conforme você chega perto de um amigo.
- **Amigos fixos**, com aviso quando um deles chega perto.
- **Alerta urgente**: mensagem com vibração forte para todo mundo no alcance.
- **Mural que some**: mostra só as mensagens das últimas horas (1h, 3h, 6h ou sempre).
- **Temas de cor**: roxo, verde-água ou coral, no modo claro ou escuro.
- **Visual novo**: fonte comum no lugar da fonte de terminal, bolhas de conversa e ícone próprio.
- **Sinal ao vivo**: o sinal de quem já está conectado continua sendo atualizado (no original,
  ele ficava parado na primeira medida).

## Estrutura

| Pasta / arquivo | O que é |
| --- | --- |
| `android/` | O app Android (projeto Gradle, Kotlin + Jetpack Compose) |
| `android/app/src/main/java/com/bitchat/android/ui/PertoHome.kt` | Telas novas do Perto (abas, radar, achar, alerta, configurações) |
| `android/app/src/main/java/com/bitchat/android/ui/PertoPrefs.kt` | Preferências do Perto (tema e tempo do Mural) |
| `prototipo.html` | Protótipo clicável da interface, para abrir no navegador |
| `docs/` | Site do Perto (GitHub Pages) |
| `build-e-instala.ps1` | Compila e instala nos celulares ligados no cabo (Windows) |
| `testar.ps1` | Roda os testes automáticos e o lint |
| `versao-final.ps1` | Compila a versão final assinada e confere a assinatura |

## Como compilar

Precisa do **JDK 21** e do **Android SDK** (o Android Studio já instala o SDK).

No Windows (PowerShell), dentro desta pasta:

```powershell
& .\build-e-instala.ps1
```

Ou direto pelo Gradle:

```sh
cd android
./gradlew :app:assembleDebug
```

O APK fica em `android/app/build/outputs/apk/debug/`.

Para testar a rede de verdade, são necessários **dois celulares Android** com o Perto,
com Bluetooth ligado e as permissões aceitas.

## Versão final assinada

As versões publicadas são assinadas com a chave própria do Perto. A chave **não fica neste
repositório**: o Gradle procura em `~/.perto/keystore.properties` (ou no arquivo apontado pela
variável `PERTO_KEYSTORE_PROPERTIES`), com este formato:

```properties
storeFile=/caminho/para/perto-release.jks
storePassword=...
keyAlias=perto
keyPassword=...
```

Com a chave no lugar, `& .\versao-final.ps1` compila e confere a assinatura. Sem a chave, a
versão de teste (`assembleDebug`) continua funcionando normalmente.

Impressão digital (SHA-256) do certificado oficial do Perto, para conferir um APK baixado:

```
05:D5:2B:A2:7C:FC:22:5A:A4:15:2E:B6:89:EF:86:D6:20:10:B6:1F:34:90:7B:3D:FD:D4:2B:CD:BB:FF:BC:0D
```

## Licença

Assim como o bitchat para Android, o Perto é distribuído sob a **GNU GPL v3**. Veja
[`LICENSE.md`](LICENSE.md). Quem distribuir o app precisa manter o código aberto sob a mesma
licença.

Créditos da rede Bluetooth, da criptografia e da base do app: equipe e colaboradores do
[bitchat](https://github.com/permissionlesstech/bitchat-android).
