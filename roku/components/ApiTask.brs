' Tarefa de rede. Recebe m.top.request = { kind: ..., ... } e devolve
' m.top.content (lista de itens, quando houver) e m.top.result = { ok, error, ... }.

sub init()
    m.top.functionName = "runTask"
end sub

sub runTask()
    req = m.top.request
    acc = m.global.account
    kind = ToS(req.kind)
    res = { ok: false, error: "Pedido desconhecido." }
    if kind = "login" then
        res = doLogin(req.account)
    else if kind = "cats" then
        res = doCats(acc, ToS(req.type))
    else if kind = "items" then
        res = doItems(acc, ToS(req.type), ToS(req.cat))
    else if kind = "episodes" then
        res = doEpisodes(acc, ToS(req.sid))
    else if kind = "search" then
        res = doSearch(acc, ToS(req.query))
    else if kind = "epg" then
        res = doEpg(acc, ToS(req.sid))
    end if
    m.top.result = res
end sub

' ---------------------------------------------------------------- HTTP
function httpGet(url as string, timeoutMs = 30000 as integer) as object
    xfer = CreateObject("roUrlTransfer")
    port = CreateObject("roMessagePort")
    xfer.SetMessagePort(port)
    xfer.SetUrl(url)
    ' Certificados sempre, porque um endereço http:// pode redirecionar para https://
    xfer.SetCertificatesFile("common:/certs/ca-bundle.crt")
    xfer.InitClientCertificates()
    xfer.EnableEncodings(true)
    xfer.RetainBodyOnError(true)
    if not xfer.AsyncGetToString() then
        return { ok: false, error: "Não foi possível conectar ao servidor." }
    end if
    msg = wait(timeoutMs, port)
    if msg = invalid then
        xfer.AsyncCancel()
        return { ok: false, error: "O servidor demorou demais para responder." }
    end if
    if type(msg) <> "roUrlEvent" then
        return { ok: false, error: "Erro de conexão." }
    end if
    code = msg.GetResponseCode()
    if code >= 200 and code < 300 then
        return { ok: true, body: msg.GetString() }
    end if
    if code < 0 then
        return { ok: false, error: "Não foi possível conectar ao servidor. Confira o endereço e a internet." }
    end if
    return { ok: false, error: "O servidor respondeu com erro " + code.ToStr() + "." }
end function

function httpJson(url as string, timeoutMs = 30000 as integer) as object
    r = httpGet(url, timeoutMs)
    if not r.ok then return r
    data = ParseJson(r.body)
    if data = invalid then
        return { ok: false, error: "O servidor respondeu em um formato inesperado." }
    end if
    return { ok: true, data: data }
end function

function esc(s as string) as string
    return CreateObject("roUrlTransfer").Escape(s)
end function

function apiUrl(acc as object, action as string, extra as string) as string
    u = ToS(acc.server) + "/player_api.php?username=" + esc(ToS(acc.user)) + "&password=" + esc(ToS(acc.pass))
    if action <> "" then u = u + "&action=" + action + extra
    return u
end function

' ---------------------------------------------------------------- cache
function cacheGet(key as string) as dynamic
    c = m.global.cache
    if c = invalid then return invalid
    if c.hasField(key) then return c.getField(key)
    return invalid
end function

sub cacheSet(key as string, value as object)
    c = m.global.cache
    if c = invalid then return
    if c.hasField(key) then
        c.setField(key, value)
    else
        f = {}
        f[key] = value
        c.addFields(f)
    end if
end sub

' Itens das listas ficam como arrays de { t: nome, i: imagem, u: endereço, k: tipo, s: id, c: categoria }
' e só viram ContentNode na hora de mostrar.
function nodesFrom(arr as object) as object
    out = CreateObject("roSGNode", "ContentNode")
    for each o in arr
        AddItem(out, o.t, o.i, o.u, o.k, o.s, o.c)
    end for
    return out
end function

' ---------------------------------------------------------------- login
function doLogin(a as object) as object
    if ToS(a.type) = "m3u" then
        r = m3uRoot(a)
        if not r.ok then return r
        return { ok: true, account: a }
    end if

    r = httpJson(apiUrl(a, "", ""))
    if not r.ok then return r
    if type(r.data) <> "roAssociativeArray" then
        return { ok: false, error: "Este endereço não parece ser um servidor Xtream Codes." }
    end if
    info = r.data.user_info
    if type(info) <> "roAssociativeArray" or ToS(info.auth) <> "1" then
        return { ok: false, error: "Usuário ou senha incorretos." }
    end if
    status = LCase(ToS(info.status))
    if status <> "" and status <> "active" then
        return { ok: false, error: "Sua conta está com status: " + ToS(info.status) }
    end if
    a.exp = ToS(info.exp_date)
    return { ok: true, account: a }
end function

' ---------------------------------------------------------------- lista M3U
function m3uRoot(acc as object) as object
    items = cacheGet("m3u")
    if items <> invalid then return { ok: true, items: items }
    r = httpGet(ToS(acc.m3u), 120000)
    if not r.ok then return r
    items = parseM3u(r.body)
    r = invalid
    if items.Count() = 0 then
        return { ok: false, error: "A lista não tem nenhum canal ou vídeo." }
    end if
    cacheSet("m3u", items)
    return { ok: true, items: items }
end function

function parseM3u(text as string) as object
    items = []
    q = Chr(34)
    reLogo = CreateObject("roRegex", "tvg-logo=" + q + "([^" + q + "]*)" + q, "i")
    reGroup = CreateObject("roRegex", "group-title=" + q + "([^" + q + "]*)" + q, "i")
    reName = CreateObject("roRegex", "tvg-name=" + q + "([^" + q + "]*)" + q, "i")
    hasName = false
    name = ""
    logo = ""
    group = ""
    for each raw in text.Split(Chr(10))
        l = raw.Trim()
        if l = "" then
            ' linha vazia
        else if UCase(Left(l, 7)) = "#EXTINF" then
            logo = ""
            mm = reLogo.Match(l)
            if mm.Count() > 1 then logo = mm[1]
            group = "Sem categoria"
            mm = reGroup.Match(l)
            if mm.Count() > 1 then
                if mm[1] <> "" then group = mm[1]
            end if
            name = titleOf(l)
            if name = "" then
                name = "Sem nome"
                mm = reName.Match(l)
                if mm.Count() > 1 then
                    if mm[1] <> "" then name = mm[1]
                end if
            end if
            hasName = true
        else if Left(l, 1) <> "#" and hasName then
            items.Push({ t: name, i: logo, u: l, k: kindOf(l), s: l, c: group })
            hasName = false
        end if
    end for
    return items
end function

' Nome do item: o texto depois da primeira vírgula que não está entre aspas
function titleOf(line as string) as string
    q = Chr(34)
    i = 1
    inQuote = false
    while true
        nq = Instr(i, line, q)
        if inQuote then
            if nq = 0 then return ""
            inQuote = false
            i = nq + 1
        else
            nc = Instr(i, line, ",")
            if nc > 0 and (nq = 0 or nc < nq) then return Mid(line, nc + 1).Trim()
            if nq = 0 then return ""
            inQuote = true
            i = nq + 1
        end if
    end while
    return ""
end function

function kindOf(url as string) as string
    u = LCase(url)
    if Instr(1, u, "/movie/") > 0 then return "movie"
    if Right(u, 4) = ".mp4" or Right(u, 4) = ".mkv" or Right(u, 4) = ".avi" then return "movie"
    if Instr(1, u, "/series/") > 0 then return "series"
    return "live"
end function

' ---------------------------------------------------------------- categorias
function doCats(acc as object, typ as string) as object
    out = CreateObject("roSGNode", "ContentNode")
    all = out.createChild("ContentNode")
    all.title = "Todos"
    all.shortdescriptionline2 = "all"

    if ToS(acc.type) = "m3u" then
        r = m3uRoot(acc)
        if not r.ok then return r
        seen = {}
        seen.SetModeCaseSensitive()
        for each o in r.items
            g = o.c
            if o.k = typ and not seen.DoesExist(g) then
                seen[g] = true
                c = out.createChild("ContentNode")
                c.title = g
                c.shortdescriptionline2 = g
            end if
        end for
        m.top.content = out
        return { ok: true }
    end if

    actions = { live: "get_live_categories", movie: "get_vod_categories", series: "get_series_categories" }
    r = httpJson(apiUrl(acc, actions[typ], ""))
    if not r.ok then return r
    if type(r.data) = "roArray" then
        for each o in r.data
            if type(o) = "roAssociativeArray" then
                c = out.createChild("ContentNode")
                c.title = ToS(o.category_name)
                c.shortdescriptionline2 = ToS(o.category_id)
            end if
        end for
    end if
    m.top.content = out
    return { ok: true }
end function

' ---------------------------------------------------------------- itens
function doItems(acc as object, typ as string, cat as string) as object
    if cat = "all" then
        r = getList(acc, typ)
        if not r.ok then return r
        m.top.content = nodesFrom(r.items)
        return { ok: true }
    end if

    ' Se a lista completa já está no cache, só filtra; senão pede só a categoria ao servidor
    full = invalid
    if ToS(acc.type) = "m3u" then
        r = getList(acc, typ)
        if not r.ok then return r
        full = r.items
    else
        full = cacheGet("list_" + typ)
    end if
    if full <> invalid then
        out = CreateObject("roSGNode", "ContentNode")
        for each o in full
            if o.c = cat then AddItem(out, o.t, o.i, o.u, o.k, o.s, o.c)
        end for
        m.top.content = out
        return { ok: true }
    end if

    key = SanitizeKey("cat_" + typ + "_" + cat)
    items = cacheGet(key)
    if items = invalid then
        r = fetchList(acc, typ, "&category_id=" + esc(cat))
        if not r.ok then return r
        items = r.items
        cacheSet(key, items)
    end if
    m.top.content = nodesFrom(items)
    return { ok: true }
end function

' Lista completa de um tipo (live / movie / series), guardada no cache
function getList(acc as object, typ as string) as object
    if ToS(acc.type) = "m3u" then
        r = m3uRoot(acc)
        if not r.ok then return r
        items = []
        for each o in r.items
            if o.k = typ then items.Push(o)
        end for
        return { ok: true, items: items }
    end if

    key = "list_" + typ
    items = cacheGet(key)
    if items <> invalid then return { ok: true, items: items }
    r = fetchList(acc, typ, "")
    if not r.ok then return r
    cacheSet(key, r.items)
    return r
end function

' Pede ao servidor Xtream os itens de um tipo (todos ou de uma categoria)
function fetchList(acc as object, typ as string, extra as string) as object
    actions = { live: "get_live_streams", movie: "get_vod_streams", series: "get_series" }
    r = httpJson(apiUrl(acc, actions[typ], extra), 60000)
    if not r.ok then return r

    items = []
    base = ToS(acc.server)
    u = ToS(acc.user)
    p = ToS(acc.pass)
    if type(r.data) = "roArray" then
        for each o in r.data
            if type(o) = "roAssociativeArray" then
                if typ = "live" then
                    sid = ToS(o.stream_id)
                    ' A Roku toca canais ao vivo em HLS (.m3u8)
                    items.Push({ t: ToS(o.name), i: ToS(o.stream_icon), u: base + "/live/" + u + "/" + p + "/" + sid + ".m3u8", k: "live", s: sid, c: ToS(o.category_id) })
                else if typ = "movie" then
                    sid = ToS(o.stream_id)
                    ext = ToS(o.container_extension)
                    if ext = "" then ext = "mp4"
                    items.Push({ t: ToS(o.name), i: ToS(o.stream_icon), u: base + "/movie/" + u + "/" + p + "/" + sid + "." + ext, k: "movie", s: sid, c: ToS(o.category_id) })
                else
                    items.Push({ t: ToS(o.name), i: ToS(o.cover), u: "", k: "series", s: ToS(o.series_id), c: ToS(o.category_id) })
                end if
            end if
        end for
    end if
    return { ok: true, items: items }
end function

' ---------------------------------------------------------------- episódios
function doEpisodes(acc as object, sid as string) as object
    r = httpJson(apiUrl(acc, "get_series_info", "&series_id=" + esc(sid)))
    if not r.ok then return r
    data = r.data
    if type(data) <> "roAssociativeArray" then
        return { ok: false, error: "Não foi possível carregar os episódios." }
    end if

    plot = ""
    if type(data.info) = "roAssociativeArray" then plot = ToS(data.info.plot)

    out = CreateObject("roSGNode", "ContentNode")
    eps = data.episodes
    if type(eps) = "roAssociativeArray" then
        keys = []
        for each k in eps
            keys.Push({ n: Val(k), k: k })
        end for
        keys.SortBy("n")
        for each s in keys
            addEpisodes(out, acc, eps[s.k], s.k)
        end for
    else if type(eps) = "roArray" then
        for i = 0 to eps.Count() - 1
            addEpisodes(out, acc, eps[i], (i + 1).ToStr())
        end for
    end if
    m.top.content = out
    return { ok: true, plot: plot }
end function

sub addEpisodes(out as object, acc as object, list as dynamic, seasonKey as string)
    if type(list) <> "roArray" then return
    i = 0
    for each e in list
        i = i + 1
        if type(e) = "roAssociativeArray" then
            num = ToS(e.episode_num)
            if num = "" then num = i.ToStr()
            season = ToS(e.season)
            if season = "" then season = seasonKey
            t = ToS(e.title)
            if t = "" then t = "Episódio " + num
            ext = ToS(e.container_extension)
            if ext = "" then ext = "mp4"
            icon = ""
            if type(e.info) = "roAssociativeArray" then icon = ToS(e.info.movie_image)
            eid = ToS(e.id)
            url = ToS(acc.server) + "/series/" + ToS(acc.user) + "/" + ToS(acc.pass) + "/" + eid + "." + ext
            AddItem(out, "T" + season + " · E" + num + " — " + t, icon, url, "movie", eid, "")
        end if
    end for
end sub

' ---------------------------------------------------------------- busca
function doSearch(acc as object, query as string) as object
    q = LCase(query.Trim())
    out = CreateObject("roSGNode", "ContentNode")
    labels = { live: "TV", movie: "Filme", series: "Série" }
    total = 0
    for each typ in ["live", "movie", "series"]
        r = getList(acc, typ)
        if r.ok then
            for each o in r.items
                if Instr(1, LCase(o.t), q) > 0 then
                    c = AddItem(out, o.t, o.i, o.u, o.k, o.s, o.c)
                    c.secondarytitle = labels[typ]
                    total = total + 1
                    if total >= 300 then exit for
                end if
            end for
        end if
        if total >= 300 then exit for
    end for
    m.top.content = out
    return { ok: true, count: total }
end function

' ---------------------------------------------------------------- guia (EPG)
function doEpg(acc as object, sid as string) as object
    r = httpJson(apiUrl(acc, "get_short_epg", "&stream_id=" + esc(sid) + "&limit=2"))
    if not r.ok then return r
    text = ""
    if type(r.data) = "roAssociativeArray" then
        l = r.data.epg_listings
        if type(l) = "roArray" then
            for i = 0 to l.Count() - 1
                e = l[i]
                if type(e) = "roAssociativeArray" then
                    label = "Agora  "
                    if i > 0 then label = "Depois  "
                    if text <> "" then text = text + Chr(10)
                    text = text + label + epgTime(e) + "  " + fromB64(ToS(e.title))
                end if
            end for
        end if
    end if
    return { ok: true, text: text }
end function

function epgTime(e as object) as string
    ts = ToS(e.start_timestamp)
    if ts <> "" and ts <> "0" then
        dt = CreateObject("roDateTime")
        dt.FromSeconds(ts.ToInt())
        dt.ToLocalTime()
        return Pad2(dt.GetHours()) + ":" + Pad2(dt.GetMinutes())
    end if
    return Mid(ToS(e.start), 12, 5)
end function

function fromB64(s as string) as string
    if s = "" then return s
    ba = CreateObject("roByteArray")
    ba.FromBase64String(s)
    r = ba.ToAsciiString()
    if r = "" then return s
    return r
end function
