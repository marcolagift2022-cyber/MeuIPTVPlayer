sub init()
    m.video = m.top.findNode("video")
    m.osd = m.top.findNode("osd")
    m.name = m.top.findNode("name")
    m.epg = m.top.findNode("epg")
    m.keys = m.top.findNode("keys")
    m.err = m.top.findNode("err")
    m.osdTimer = m.top.findNode("osdTimer")
    m.osdTimer.observeField("fire", "hideOsd")
    m.video.observeField("state", "onState")
    m.isLive = false
    m.retried = false
    m.index = 0
end sub

sub onParams()
    p = m.top.params
    m.content = p.content
    play(p.index)
end sub

sub doFocus()
    m.video.setFocus(true)
end sub

sub play(i as integer)
    if m.content = invalid then return
    item = m.content.getChild(i)
    if item = invalid then return
    m.index = i
    m.item = item
    m.retried = false
    m.isLive = (item.shortdescriptionline1 = "live")
    m.name.text = item.title
    m.epg.text = ""
    if m.isLive then
        m.keys.text = "Cima/Baixo: trocar canal     *: favoritar"
    else
        m.keys.text = "*: favoritar"
    end if
    startVideo(item.url)
    showOsd()
    if m.isLive then loadEpg()
end sub

sub startVideo(url as string)
    m.err.visible = false
    c = CreateObject("roSGNode", "ContentNode")
    c.url = url
    c.title = m.item.title
    c.streamFormat = formatOf(url)
    c.live = m.isLive
    m.video.control = "stop"
    m.video.enableUI = not m.isLive
    m.video.content = c
    m.video.control = "play"
end sub

' Formato do vídeo pelo final do endereço
function formatOf(url as string) as string
    u = LCase(url)
    q = Instr(1, u, "?")
    if q > 0 then u = Left(u, q - 1)
    if Right(u, 5) = ".m3u8" then return "hls"
    if Right(u, 4) = ".mpd" then return "dash"
    if Right(u, 4) = ".mkv" then return "mkv"
    if Right(u, 4) = ".mp4" or Right(u, 4) = ".m4v" or Right(u, 4) = ".mov" then return "mp4"
    if m.isLive then return "hls"
    return "mp4"
end function

' Canais de lista M3U costumam vir em .ts ou sem extensão; a Roku toca melhor em .m3u8
function altUrl(url as string) as string
    if Instr(1, url, "?") > 0 then return ""
    if LCase(Right(url, 3)) = ".ts" then return Left(url, Len(url) - 3) + ".m3u8"
    parts = url.Split("/")
    seg = parts[parts.Count() - 1]
    if Instr(1, seg, ".") = 0 then return url + ".m3u8"
    return ""
end function

sub onState()
    s = m.video.state
    if s = "error" then
        if m.isLive and not m.retried then
            alt = altUrl(m.item.url)
            if alt <> "" then
                m.retried = true
                startVideo(alt)
                return
            end if
        end if
        m.err.text = "Não foi possível reproduzir este conteúdo." + Chr(10) + m.video.errorMsg
        m.err.visible = true
        showOsd()
    else if s = "playing" then
        m.err.visible = false
    else if s = "finished" and not m.isLive then
        m.top.getScene().callFunc("goBack", {})
    end if
end sub

sub zap(dir as integer)
    n = m.content.getChildCount()
    i = m.index
    for k = 1 to n
        i = (i + dir + n) mod n
        c = m.content.getChild(i)
        if c <> invalid then
            if c.shortdescriptionline1 = "live" then
                play(i)
                return
            end if
        end if
    end for
end sub

sub loadEpg()
    if ToS(m.global.account.type) <> "xtream" then return
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { kind: "epg", sid: m.item.shortdescriptionline2 }
    t.observeField("result", "onEpg")
    m.epgTask = t
    t.control = "run"
end sub

sub onEpg(ev as object)
    t = ev.getRoSGNode()
    if ToS(t.request.sid) <> m.item.shortdescriptionline2 then return
    res = ev.getData()
    if res.ok = true then m.epg.text = ToS(res.text)
end sub

sub showOsd()
    m.osd.visible = true
    m.osdTimer.control = "stop"
    m.osdTimer.control = "start"
end sub

sub hideOsd()
    m.osd.visible = false
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back" then
        m.video.control = "stop"
        return false
    end if
    if key = "options" then
        m.top.getScene().callFunc("showToast", { text: ToggleFav(m.item) })
        return true
    end if
    if m.isLive then
        if key = "up" or key = "channelup" then
            zap(1)
            return true
        else if key = "down" or key = "channeldown" then
            zap(-1)
            return true
        else if key = "OK" or key = "info" then
            showOsd()
            return true
        end if
    end if
    return false
end function
