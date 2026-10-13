# X BR TOP CINE — Web Player

Versão para navegador (computador e celular), feita a partir do app da TV LG.

- **Login:** só usuário e senha. Os servidores (DNS) vêm do `config.json` na raiz do repositório,
  na mesma ordem do app Android. O app tenta um por um até algum aceitar.
- **Telas:** TV ao vivo, Filmes, Séries (com episódios), Favoritos (estrela ☆ em cada item), Busca,
  quantidade de itens em cada categoria e aviso do `config.json` ("aviso") no menu.
- **Controles:** mouse, toque no celular e teclado (setas, Enter, Esc para voltar, F para tela cheia).
  O botão "voltar" do navegador/celular também funciona.
- **Player:** hls.js (carregado do jsDelivr) para canais .m3u8. No ao vivo, ▲▼ trocam de canal.

## Arquivos
- `index.html`, `css/style.css`, `js/app.js`, `img/` (logo, fundo e as artes do menu).
- Para publicar, é só copiar a pasta `web/` inteira para a hospedagem.

## Hospedagem (a decidir)
- O DNS atual é `http://`. Navegadores bloqueiam vídeo/API `http` dentro de um site `https`
  ("conteúdo misto"). Além disso, muitos painéis Xtream não liberam acesso de outros sites (CORS).
- Opção simples: hospedar o site em `http`.
- Opção com cadeado: site em `https` + um proxy. Quando existir, é só colocar no `config.json`:
  `"web_proxy": "https://seu-proxy.com/?url="` — o player passa a usar o proxy sem mudar código.
