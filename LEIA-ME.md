# Meu IPTV Player — guia para quem nunca programou

Este é um reprodutor de IPTV para Android no estilo do XCIPTV. Funciona em **celular, TV Box e Android TV** (com toque ou controle remoto).

## O que o app já faz

- Login por **Xtream Codes** (servidor + usuário + senha) ou por **link de lista M3U**
- **TV ao vivo**, **Filmes** e **Séries** separados por categoria, com logos e capas
- **Busca** dentro da categoria
- **Favoritos** (toque e segure o item no celular, ou aperte **Menu** no controle)
- **Player** com reconexão automática quando o sinal cai
- **Troca de canal** pelo controle: setas ▲▼ ou CH+/CH− (no celular: botões ⏮ ⏭)
- **EPG**: mostra o programa de agora e o próximo (contas Xtream)
- Séries: ao terminar um episódio, o próximo começa sozinho
- Lembra o login: na próxima vez abre direto no menu

---

## Passo 1 — Instalar o Android Studio (uma vez só)

1. Acesse **developer.android.com/studio** e baixe o Android Studio (é gratuito).
2. Instale aceitando as opções padrão. Na primeira abertura ele baixa vários componentes — deixe terminar (pode levar uns 20 minutos).
3. Precisa de um computador com Windows, Mac ou Linux e de preferência 8 GB de RAM ou mais.

## Passo 2 — Abrir o projeto

1. Descompacte o arquivo **MeuIPTVPlayer.zip** numa pasta fácil (ex: `Documentos`).
2. No Android Studio: **File → Open** (ou "Open" na tela de boas-vindas) e escolha a pasta **MeuIPTVPlayer**.
3. Se perguntar "Trust project?", clique em **Trust Project**.
4. Espere a barra lá embaixo terminar ("Gradle sync"). Na primeira vez ele baixa as bibliotecas do player — precisa de internet e pode demorar alguns minutos.
   - Se aparecer um aviso sugerindo atualizar o "Android Gradle Plugin", pode **ignorar** (ou aceitar — os dois funcionam).

## Passo 3 — Testar no seu celular

1. No celular: **Configurações → Sobre o telefone** → toque **7 vezes** em "Número da versão" (ou "Número de compilação"). Aparece "Você agora é um desenvolvedor".
2. Volte em Configurações → **Opções do desenvolvedor** → ative **Depuração USB**.
3. Ligue o celular no computador com o cabo USB e aceite a mensagem "Permitir depuração USB?".
4. No Android Studio, o nome do celular aparece no topo. Clique no botão verde **▶ Run**.
5. O app abre sozinho no celular. 🎉

Sem celular à mão? Use o emulador: **Tools → Device Manager → Create Device** (escolha um "TV" para ver como fica na televisão).

## Passo 4 — Gerar o APK para instalar na TV Box

1. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   - Para uma versão mais leve e rápida: troque, no canto inferior esquerdo, a aba **Build Variants** de `debug` para `release` antes de gerar.
2. Quando terminar, clique em **locate** na mensagem que aparece. O arquivo é o `app-debug.apk` (ou `app-release.apk`).
3. Copie o APK para um **pendrive** e abra na TV Box com um gerenciador de arquivos — ou envie pelo app **Downloader** / **Send Files to TV**.
4. Na TV Box, permita "instalar apps de fontes desconhecidas" quando pedir.

---

## Trocar o DNS (servidor) de todos os clientes

O endereço do servidor NÃO fica dentro do app: ele vem do arquivo **config.json** aqui no GitHub.
Os clientes só digitam usuário e senha.

1. Abra **github.com/marcolagift2022-cyber/MeuIPTVPlayer** e clique em **config.json**.
2. Clique no **lápis** (✏️ Edit) no canto direito.
3. Troque o endereço e clique em **Commit changes** (duas vezes).

```json
{
  "dns": [
    "http://servidor-principal.com",
    "http://servidor-reserva.com:8080"
  ],
  "aviso": "Texto que aparece no menu dos clientes (deixe \"\" para não mostrar nada)"
}
```

- O app tenta os DNS **na ordem**: se o primeiro cair, usa o próximo sozinho.
- A mudança chega aos clientes na próxima vez que abrirem o app (pode levar até ~5 minutos).
- Cuidado com as aspas e vírgulas: entre um DNS e outro vai vírgula, depois do último não.

## Personalizar

| O que mudar | Onde |
|---|---|
| Nome do app | `app/src/main/res/values/strings.xml` |
| Cores | `app/src/main/res/values/colors.xml` (`accent` é a cor de destaque) |
| Ícone | Arquivos `ic_launcher.png` nas pastas `app/src/main/res/mipmap-*` (ou no Android Studio: botão direito em `res` → **New → Image Asset**) |
| Banner da Android TV | `app/src/main/res/drawable-xhdpi/banner.png` (640×360) |
| Identificador do app | `applicationId` em `app/build.gradle.kts` (ex: `com.seunome.iptv`) |
| Canais ao vivo em HLS em vez de TS | Em `XtreamApi.kt`, função `liveUrl`, troque `.ts` por `.m3u8` |

## Onde fica cada parte (para quem quiser mexer)

- `LoginActivity.kt` — tela de login
- `MainActivity.kt` — menu principal
- `ListActivity.kt` — categorias e grade de canais/filmes/séries/favoritos
- `EpisodesActivity.kt` — episódios de uma série
- `PlayerActivity.kt` — o player de vídeo
- `XtreamApi.kt` — conversa com o painel Xtream
- `M3uParser.kt` — lê listas M3U
- `Repository.kt` — junta tudo e guarda em memória
- `Prefs.kt` — salva login e favoritos

## Problemas comuns

- **"Usuário ou senha incorretos"** — confira se o servidor tem a porta (ex: `http://servidor.com:8080`).
- **Canal não abre / fica carregando** — teste o mesmo canal em outro app. Se funcionar lá, tente trocar `.ts` por `.m3u8` (tabela acima).
- **Gradle sync falhou** — veja se o computador está com internet e clique em **File → Sync Project with Gradle Files**.

## Publicar na Play Store (opcional)

1. Crie uma conta de desenvolvedor no **Google Play Console** (taxa única de US$ 25).
2. Gere uma chave própria: **Build → Generate Signed App Bundle / APK** e guarde muito bem o arquivo e a senha — sem eles você não consegue atualizar o app.
3. Troque o `applicationId` por um seu.
4. Importante: a Google aceita reprodutores de IPTV, **desde que o app não venha com canais, listas ou conteúdo**, e que a descrição deixe claro que o usuário precisa ter a própria assinatura legítima. Não use nomes, logos ou prints de canais e marcas famosas na página do app.
