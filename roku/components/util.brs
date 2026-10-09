' Funções de apoio usadas por várias telas e pela tarefa de rede.

' Converte qualquer valor vindo do JSON em texto ("" quando vazio)
function ToS(v as dynamic) as string
    t = LCase(type(v))
    if t = "invalid" or t = "roinvalid" then return ""
    if t = "string" or t = "rostring" then return v
    if Instr(1, t, "int") > 0 then return v.ToStr()
    if Instr(1, t, "float") > 0 or Instr(1, t, "double") > 0 then
        if v = Int(v) then return Int(v).ToStr()
        return Str(v).Trim()
    end if
    if t = "boolean" or t = "roboolean" then
        if v then return "true"
        return "false"
    end if
    return ""
end function

function Pad2(n as integer) as string
    if n < 10 then return "0" + n.ToStr()
    return n.ToStr()
end function

function NormalizeUrl(s as string) as string
    s = s.Trim()
    if s = "" then return s
    l = LCase(s)
    if Left(l, 7) <> "http://" and Left(l, 8) <> "https://" then s = "http://" + s
    while Len(s) > 0 and Right(s, 1) = "/"
        s = Left(s, Len(s) - 1)
    end while
    return s
end function

function SanitizeKey(k as string) as string
    re = CreateObject("roRegex", "[^A-Za-z0-9_]", "")
    return "k_" + re.ReplaceAll(k, "_")
end function

' ---------------------------------------------------------------- registro
function RegRead(key as string) as string
    sec = CreateObject("roRegistrySection", "xbrtopcine")
    if sec.Exists(key) then return sec.Read(key)
    return ""
end function

function RegWrite(key as string, value as string) as boolean
    sec = CreateObject("roRegistrySection", "xbrtopcine")
    if not sec.Write(key, value) then return false
    return sec.Flush()
end function

sub RegDelete(key as string)
    sec = CreateObject("roRegistrySection", "xbrtopcine")
    sec.Delete(key)
    sec.Flush()
end sub

function LoadAccount() as dynamic
    s = RegRead("account")
    if s = "" then return invalid
    acc = ParseJson(s)
    if type(acc) <> "roAssociativeArray" then return invalid
    return acc
end function

sub SaveAccount(acc as object)
    RegWrite("account", FormatJson(acc))
end sub

sub ClearAccount()
    RegDelete("account")
end sub

' ---------------------------------------------------------------- itens
' Cada item é um ContentNode:
'   title, hdposterurl (capa/logo), url (vídeo)
'   shortdescriptionline1 = tipo (live / movie / series)
'   shortdescriptionline2 = id no servidor
'   description = categoria
function AddItem(parent as object, title as string, icon as string, url as string, kind as string, sid as string, cat as string) as object
    n = parent.createChild("ContentNode")
    n.setFields({ title: title, hdposterurl: icon, url: url, shortdescriptionline1: kind, shortdescriptionline2: sid, description: cat })
    return n
end function

function CopyItem(parent as object, src as object) as object
    return AddItem(parent, src.title, src.hdposterurl, src.url, src.shortdescriptionline1, src.shortdescriptionline2, src.description)
end function

' A tela ainda está aberta? (evita mexer no foco depois que o usuário saiu dela)
function IsActive() as boolean
    return m.top.getParent() <> invalid and m.top.visible
end function

' Abre um item: série do Xtream vai para a lista de episódios; o resto toca direto
sub OpenItem(scene as object, content as object, idx as integer)
    item = content.getChild(idx)
    if item = invalid then return
    if item.shortdescriptionline1 = "series" and item.url = "" then
        scene.callFunc("navigate", { view: "EpisodesView", params: { sid: item.shortdescriptionline2, title: item.title, icon: item.hdposterurl } })
    else
        scene.callFunc("navigate", { view: "PlayerView", params: { content: content, index: idx } })
    end if
end sub

' ---------------------------------------------------------------- favoritos
function LoadFavs() as object
    s = RegRead("favs")
    if s <> "" then
        a = ParseJson(s)
        if type(a) = "roArray" then return a
    end if
    return []
end function

' Adiciona ou remove o item dos favoritos e devolve a mensagem para mostrar
function ToggleFav(item as object) as string
    favs = LoadFavs()
    kind = item.shortdescriptionline1
    sid = item.shortdescriptionline2
    for i = 0 to favs.Count() - 1
        f = favs[i]
        if type(f) = "roAssociativeArray" then
            if ToS(f.kind) = kind and ToS(f.sid) = sid then
                favs.Delete(i)
                RegWrite("favs", FormatJson(favs))
                return "Removido dos favoritos"
            end if
        end if
    end for
    favs.Push({ kind: kind, sid: sid, title: item.title, icon: item.hdposterurl, url: item.url })
    json = FormatJson(favs)
    ' A Roku guarda no máximo 16 KB por aplicativo
    if Len(json) > 12000 then return "Não cabem mais favoritos. Remova algum primeiro."
    if not RegWrite("favs", json) then return "Não foi possível salvar o favorito."
    return "Adicionado aos favoritos"
end function

function FavsToContent(cat as string) as object
    c = CreateObject("roSGNode", "ContentNode")
    for each f in LoadFavs()
        if type(f) = "roAssociativeArray" then
            kind = ToS(f.kind)
            if cat = "all" or kind = cat then
                AddItem(c, ToS(f.title), ToS(f.icon), ToS(f.url), kind, ToS(f.sid), "")
            end if
        end if
    end for
    return c
end function
