# X BR TOP CINE — versão Roku

Reprodutor genérico, igual à versão LG: o cliente entra com a conta Xtream Codes (servidor, usuário e senha) **ou** com o link de uma lista M3U. O app não traz nenhum canal ou conteúdo.

## Onde baixar
GitHub → **Releases** → a versão mais nova chamada **"Roku 1.0.N"** → arquivo `XBRTopCine-Roku-1.0.N.zip`.
**Não descompacte o .zip.** A Roku recebe o arquivo .zip inteiro.

---

## 1) Testar numa Roku (modo desenvolvedor)

### Ligar o modo desenvolvedor (só uma vez)
1. Ligue a Roku na **mesma internet (Wi-Fi)** do computador.
2. No controle da Roku, aperte em sequência:
   **Início 3x · Cima 2x · Direita · Esquerda · Direita · Esquerda · Direita**
3. Vai abrir a tela **Developer Settings**. Anote o endereço que aparece (exemplo: `http://192.168.0.15`).
4. Escolha **Enable installer and restart**, aceite os termos e **crie uma senha** (anote).
5. A Roku reinicia.

### Instalar o app
1. No computador, abra o navegador e digite o endereço anotado (ex.: `http://192.168.0.15`).
2. Usuário: **rokudev** · Senha: a que você criou.
3. Clique em **Upload**, escolha o arquivo `XBRTopCine-Roku-1.0.N.zip` e clique em **Install** (ou **Replace**, se já tiver um instalado).
4. O app abre sozinho na TV.

### O que testar
- Entrar com a lista de demonstração (escolha "Lista M3U"):
  `https://raw.githubusercontent.com/marcolagift2022-cyber/MeuIPTVPlayer/main/loja/demo.m3u`
- Entrar com uma conta Xtream de verdade.
- TV ao vivo: abrir canal, trocar com **Cima/Baixo**.
- Filmes e séries: abrir, assistir, pausar, avançar.
- Buscar, favoritar (**botão \*** do controle), sair da conta.

> No modo desenvolvedor, só cabe **um** app de teste por vez na Roku.

---

## 2) Canal beta (para alguns clientes testarem)
1. Crie uma conta de desenvolvedor em **developer.roku.com** (é a mesma conta Roku).
2. No painel, crie um canal novo do tipo **Beta** e envie o mesmo `.zip`.
3. Convide os testadores pelo e-mail da conta Roku deles.
4. Pelo que sabemos, o canal beta tem **limite de testadores** e **expira depois de alguns meses**. Confira as regras atuais no painel.

## 3) Loja oficial da Roku (Channel Store)
Precisa passar pela certificação da Roku. Pontos que ainda faltam antes de enviar:
- **Deep linking:** a Roku testa abrir conteúdo direto por link. Num reprodutor sem catálogo próprio talvez não se aplique, mas é preciso confirmar.
- **Política de conteúdo:** a Roku é rígida com apps de IPTV. Vale esperar a resposta da LG antes.
- Imagens da loja, descrição e política de privacidade: dá para reaproveitar o material da pasta `loja/`.

## Arquivos
- `manifest`: nome, versão, ícones e tela de abertura
- `source/main.brs`: início do app
- `components/`: telas (login, menu, lista, episódios, busca, player) e a tarefa de rede (`ApiTask`)
- `images/`: ícones, abertura e fundo
- O GitHub Actions ("Gerar app da Roku") confere o código e gera o `.zip` sozinho a cada mudança.
