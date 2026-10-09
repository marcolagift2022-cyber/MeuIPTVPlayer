sub init()
    m.menu = m.top.findNode("menu")
    m.clock = m.top.findNode("clock")
    m.exp = m.top.findNode("exp")
    m.leaving = false

    c = CreateObject("roSGNode", "ContentNode")
    items = [
        ["TV ao vivo", "Canais", "live"],
        ["Filmes", "Catálogo de filmes", "movie"],
        ["Séries", "Temporadas e episódios", "series"],
        ["Favoritos", "Seus salvos", "fav"],
        ["Buscar", "Em toda a lista", "search"],
        ["Atualizar", "Recarregar as listas", "refresh"],
        ["Sair", "Trocar de conta", "logout"]
    ]
    for each it in items
        n = c.createChild("ContentNode")
        n.title = it[0]
        n.description = it[1]
        n.shortdescriptionline1 = it[2]
    end for
    m.menu.content = c
    m.menu.observeField("itemSelected", "onSelect")

    m.clockTimer = m.top.findNode("clockTimer")
    m.clockTimer.observeField("fire", "updateClock")
    m.clockTimer.control = "start"
    updateClock()
    showExp()
end sub

sub onParams()
end sub

sub doFocus()
    m.menu.setFocus(true)
end sub

sub updateClock()
    dt = CreateObject("roDateTime")
    dt.ToLocalTime()
    m.clock.text = Pad2(dt.GetHours()) + ":" + Pad2(dt.GetMinutes())
end sub

sub showExp()
    acc = m.global.account
    if ToS(acc.type) = "m3u" then
        m.exp.text = "Lista M3U"
        return
    end if
    e = ToS(acc.exp)
    if e = "" or e = "0" or e = "null" then
        m.exp.text = "Vencimento: ilimitado"
        return
    end if
    dt = CreateObject("roDateTime")
    dt.FromSeconds(e.ToInt())
    dt.ToLocalTime()
    m.exp.text = "Vence em " + Pad2(dt.GetDayOfMonth()) + "/" + Pad2(dt.GetMonth()) + "/" + dt.GetYear().ToStr()
end sub

sub onSelect()
    act = m.menu.content.getChild(m.menu.itemSelected).shortdescriptionline1
    scene = m.top.getScene()
    if act = "live" or act = "movie" or act = "series" or act = "fav" then
        scene.callFunc("navigate", { view: "BrowseView", params: { type: act } })
    else if act = "search" then
        scene.callFunc("navigate", { view: "SearchView", params: {} })
    else if act = "refresh" then
        m.global.cache = CreateObject("roSGNode", "Node")
        scene.callFunc("showToast", { text: "Pronto! As listas serão recarregadas." })
    else if act = "logout" then
        confirmLogout()
    end if
end sub

sub confirmLogout()
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Sair da conta?"
    dlg.message = ["Você vai precisar entrar de novo com seus dados."]
    dlg.buttons = ["Sair", "Cancelar"]
    dlg.observeField("buttonSelected", "onLogoutButton")
    dlg.observeField("wasClosed", "onLogoutClosed")
    m.dlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onLogoutButton()
    if m.dlg.buttonSelected = 0 then m.leaving = true
    m.dlg.close = true
end sub

sub onLogoutClosed()
    if m.leaving then
        m.clockTimer.control = "stop"
        ClearAccount()
        m.global.account = {}
        m.global.cache = CreateObject("roSGNode", "Node")
        m.top.getScene().callFunc("resetTo", { view: "LoginView" })
    else
        m.menu.setFocus(true)
    end if
end sub
