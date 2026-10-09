# Materiais para a Google Play — X BR TOP CINE

Versão enviada à Play: **flavor "play"** (reprodutor genérico, pacote `com.xbrtopcine.player`).
A versão dos clientes pelo Downloader (`com.meuiptv.player`) continua separada e não muda.

## Arquivos
- **App (.aab):** GitHub → Releases → "Google Play 1.0.N" → `XBRTopCine-Play-1.0.N.aab`
  (só aparece depois de cadastrar o Secret `PLAY_KEY_SENHA` no GitHub)
- **APK para testar no celular:** o arquivo `...-teste.apk` da mesma release
- **Ícone 512x512:** `loja/play/icone-512.png`
- **Imagem de destaque 1024x500:** `loja/play/destaque-1024x500.png`
- **Capturas de tela do celular:** mínimo 2. Tirar no celular com o APK de teste, usando a lista de demonstração.

## Ficha da loja
- **Nome:** X BR TOP CINE
- **Descrição curta (até 80 caracteres):**
  Reprodutor de listas IPTV (Xtream Codes e M3U). Não inclui canais.
- **Descrição completa:**

X BR TOP CINE é um reprodutor de mídia para celular, tablet e TV Box. Com ele você assiste ao conteúdo do seu próprio provedor, usando uma conta Xtream Codes (servidor, usuário e senha) ou o link de uma lista M3U.

Recursos:
• TV ao vivo, filmes e séries organizados por categoria
• Busca em toda a sua lista
• Favoritos
• Guia de programação (quando o provedor oferece)
• Troca rápida de canal
• Controle dos pais com senha para categorias adultas
• Funciona com controle remoto em TV Box e Android TV

IMPORTANTE: este aplicativo NÃO fornece canais, filmes, séries ou listas e não é ligado a nenhum provedor. Você precisa ter uma assinatura ou lista própria. Use apenas conteúdo que você tem o direito de assistir.

- **Categoria:** Players e editores de vídeo (Video Players & Editors)
- **E-mail de contato:** marcolagift2022@gmail.com
- **Política de privacidade:** https://github.com/marcolagift2022-cyber/MeuIPTVPlayer/blob/main/PRIVACIDADE.md
- **Termos de uso:** https://github.com/marcolagift2022-cyber/MeuIPTVPlayer/blob/main/TERMOS.md

## Acesso ao app (para o revisor do Google)
"Todas ou algumas funcionalidades são restritas" → adicionar instruções:

    The app is a generic media player and does not include content.
    To test: open the app, tap "Lista M3U", paste the public demo playlist below and tap "Entrar":
    https://raw.githubusercontent.com/marcolagift2022-cyber/MeuIPTVPlayer/main/loja/demo.m3u
    It contains only free test videos (Big Buck Bunny etc.).

## Respostas do Play Console (sugestão)
- **Anúncios:** Não, o app não tem anúncios.
- **Público-alvo:** 18 anos ou mais. O app não é voltado para crianças.
- **Apps de notícias / saúde / finanças / governo:** Não.
- **Segurança dos dados:**
  - O app coleta ou compartilha dados? **Não.** Os dados de acesso (servidor, usuário, senha ou link) ficam só no aparelho e vão apenas para o servidor que o próprio usuário digitou. O desenvolvedor não recebe nada.
  - Criptografia em trânsito: depende do servidor do usuário (muitos usam http).
  - O usuário pode pedir exclusão? Os dados ficam só no aparelho: apagar com "Sair" ou desinstalando.
- **Classificação de conteúdo (questionário IARC):**
  - Categoria: "Todos os outros tipos de app" / reprodutor de mídia
  - Violência, sexo, linguagem, drogas: Não (o app em si não tem conteúdo)
  - Os usuários interagem ou trocam conteúdo entre si? Não
  - Compartilha localização? Não · Compras digitais? Não
- **Formatos:** começar só com **celular e tablet**. Android TV pode ser ativado depois (exige banner e capturas de TV).

## Conta nova de pessoa física
Antes de publicar para todos, o Google exige um **teste fechado com pelo menos 12 testadores por 14 dias seguidos**.
Depois disso, no Play Console aparece o pedido de "acesso à produção".
