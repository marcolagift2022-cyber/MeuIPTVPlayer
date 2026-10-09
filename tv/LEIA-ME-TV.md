# X BR TOP CINE — versão para TV LG e Samsung

Reprodutor genérico: a pessoa digita o **servidor, usuário e senha** (Xtream Codes) ou o **link M3U** do provedor dela.
O app não vem com canais nem listas.

Os arquivos ficam em **Releases** no GitHub, com nome começando por **"TV LG / Samsung"**:
https://github.com/marcolagift2022-cyber/MeuIPTVPlayer/releases

| Arquivo | Para quê |
|---|---|
| `XBRTopCine-LG-1.0.X.ipk` | Instalar na TV **LG** (webOS) |
| `XBRTopCine-Samsung-Tizen-1.0.X.zip` | Projeto para instalar na TV **Samsung** (Tizen) |
| `XBRTopCine-Web-1.0.X.zip` | Código web (para desenvolvedores) |

---

## TV LG (webOS) — modo desenvolvedor

**Na TV**
1. Crie uma conta grátis em **webostv.developer.lge.com** (botão "Sign in" → criar conta).
2. Na TV, abra a loja **LG Content Store**, procure e instale o app **"Developer Mode"**.
3. Abra o Developer Mode, entre com a conta criada e ligue **Dev Mode Status** → a TV reinicia.
4. Abra o Developer Mode de novo e ligue **Key Server**. Anote o **IP da TV** que aparece na tela.

**No computador (Windows, Mac ou Linux)** — precisa do Node.js instalado (nodejs.org)
```
npm install -g @webos-tools/cli
ares-setup-device
```
- Escolha **add**, dê o nome `tv`, coloque o **IP da TV**, porta **9922**, usuário **prisoner**.
```
ares-novacom --device tv --getkey
```
- Ele pede uma **senha (passphrase)**: é a que aparece no app Developer Mode da TV.
```
ares-install --device tv XBRTopCine-LG-1.0.X.ipk
```
Pronto: o **X BR TOP CINE** aparece na lista de apps da TV.

> O modo desenvolvedor da LG expira depois de algumas horas/dias. Para continuar usando, abra o app Developer Mode e toque em **Extend**. Isso é só para teste — para clientes, o caminho é a **LG Content Store**.

Alternativa sem linha de comando: o programa gratuito **webOS Dev Manager** (procure por "webosbrew dev manager") faz a mesma coisa com botões.

---

## TV Samsung (Tizen) — modo desenvolvedor

A Samsung exige que o app seja **assinado com um certificado ligado à TV** de quem vai testar, então isso é feito no computador da pessoa.

**No computador**
1. Instale o **Tizen Studio** (developer.tizen.org → Tizen Studio) e, no **Package Manager**, instale **TV Extensions** e **Samsung Certificate Extension**.

**Na TV**
2. Abra **Apps**, aperte **1 2 3 4 5** no controle → aparece "Developer mode". Ligue **ON**, digite o **IP do computador** e reinicie a TV.
3. Anote o **IP da TV** (Configurações → Geral → Rede → Status da rede).

**No Tizen Studio**
4. **Device Manager** → **Remote Connection** (+) → coloque o IP da TV → conecte.
5. **Certificate Manager** → **+** → **Samsung** → **TV** → crie o certificado de autor e de distribuidor (pede login da conta Samsung). Com a TV conectada, ela entra automaticamente no certificado.
6. **File → New → Tizen Project → Template → TV → Web Application → Basic Project**. Apague os arquivos do projeto criado e copie para dentro dele **todo o conteúdo do zip** `XBRTopCine-Samsung-Tizen-1.0.X.zip`.
7. Clique com o botão direito no projeto → **Run As → Tizen Web Application**.

O app instala e abre na TV.

---

## Controle remoto

| Tecla | O que faz |
|---|---|
| Setas | Navegar |
| OK | Abrir / pausar |
| Voltar | Voltar / sair do vídeo |
| ▲▼ ou CH+ CH− | Trocar de canal (TV ao vivo) |
| ◀▶ | Voltar/avançar 10 s (filmes e séries) |
| Botão **vermelho** | Favoritar / desfavoritar |

## Se algum canal não abrir
- Os canais ao vivo usam **HLS (.m3u8)** quando o painel permite (as TVs tocam HLS melhor que TS).
- Se o painel bloquear a conexão do app (erro "Não foi possível conectar"), pode ser bloqueio de CORS no servidor — me avise com um print.

## Publicar nas lojas (depois dos testes)
- **LG:** conta de vendedor em seller.lgappstv.com → enviar o `.ipk` para análise.
- **Samsung:** conta em seller.samsungapps.com → enviar o `.wgt` (gerado pelo Tizen Studio com certificado de distribuição).
- Na descrição e nas imagens: deixar claro que é **apenas um reprodutor**, sem canais, logos de emissoras ou filmes.
