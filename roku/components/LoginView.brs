sub init()
    m.form = m.top.findNode("form")
    m.msg = m.top.findNode("msg")
    m.form.observeField("itemSelected", "onSelect")
    m.acc = { type: "xtream", server: "", user: "", pass: "", m3u: "" }
    m.busy = false
    m.lastIdx = 0
    m.dlg = invalid
    buildForm(0)
end sub

sub onParams()
end sub

sub doFocus()
    m.form.setFocus(true)
end sub

sub buildForm(idx as integer)
    c = CreateObject("roSGNode", "ContentNode")
    if m.acc.type = "xtream" then
        addRow(c, "Tipo de conta:  Xtream Codes   (OK para trocar)", "type")
        addRow(c, "Servidor:  " + showVal(m.acc.server, false), "server")
        addRow(c, "Usuário:  " + showVal(m.acc.user, false), "user")
        addRow(c, "Senha:  " + showVal(m.acc.pass, true), "pass")
    else
        addRow(c, "Tipo de conta:  Lista M3U   (OK para trocar)", "type")
        addRow(c, "Link da lista:  " + showVal(m.acc.m3u, false), "m3u")
    end if
    addRow(c, "ENTRAR", "login")
    m.form.content = c
    lastIdx = c.getChildCount() - 1
    if idx > lastIdx then idx = lastIdx
    if idx < 0 then idx = 0
    m.form.jumpToItem = idx
end sub

sub addRow(c as object, title as string, act as string)
    n = c.createChild("ContentNode")
    n.title = title
    n.shortdescriptionline1 = act
end sub

function showVal(v as string, secret as boolean) as string
    if v = "" then return "(aperte OK para digitar)"
    if secret then
        s = ""
        for i = 1 to Len(v)
            s = s + "*"
        end for
        return s
    end if
    if Len(v) > 40 then return Left(v, 37) + "..."
    return v
end function

sub onSelect()
    if m.busy then return
    idx = m.form.itemSelected
    act = m.form.content.getChild(idx).shortdescriptionline1
    m.lastIdx = idx
    if act = "type" then
        if m.acc.type = "xtream" then
            m.acc.type = "m3u"
        else
            m.acc.type = "xtream"
        end if
        m.msg.text = ""
        buildForm(0)
    else if act = "login" then
        startLogin()
    else
        titles = { server: "Endereço do servidor (ex.: http://servidor.com:8080)", user: "Usuário", pass: "Senha", m3u: "Link da lista M3U" }
        openKeyboard(act, titles[act])
    end if
end sub

sub openKeyboard(field as string, title as string)
    m.editing = field
    m.kbOk = false
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = title
    dlg.text = m.acc[field]
    dlg.buttons = ["OK", "Cancelar"]
    dlg.observeField("buttonSelected", "onKbButton")
    dlg.observeField("wasClosed", "onKbClosed")
    m.dlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onKbButton()
    if m.dlg = invalid then return
    if m.dlg.buttonSelected = 0 then
        m.acc[m.editing] = m.dlg.text.Trim()
        m.kbOk = true
    end if
    m.dlg.close = true
end sub

sub onKbClosed()
    m.dlg = invalid
    idx = m.lastIdx
    if m.kbOk then idx = idx + 1
    buildForm(idx)
    m.form.setFocus(true)
end sub

sub startLogin()
    a = m.acc
    if a.type = "xtream" then
        if a.server = "" or a.user = "" or a.pass = "" then
            m.msg.color = "0xFF6B6BFF"
            m.msg.text = "Preencha servidor, usuário e senha."
            return
        end if
    else if a.m3u = "" then
        m.msg.color = "0xFF6B6BFF"
        m.msg.text = "Digite o link da lista M3U."
        return
    end if

    send = { type: a.type, server: NormalizeUrl(a.server), user: a.user, pass: a.pass, m3u: NormalizeUrl(a.m3u) }
    m.msg.color = "0xA9B5ACFF"
    m.msg.text = "Conectando..."
    m.busy = true
    m.global.cache = CreateObject("roSGNode", "Node")
    m.task = CreateObject("roSGNode", "ApiTask")
    m.task.request = { kind: "login", account: send }
    m.task.observeField("result", "onLogin")
    m.task.control = "run"
end sub

sub onLogin(ev as object)
    res = ev.getData()
    m.busy = false
    if res.ok = true then
        m.global.account = res.account
        SaveAccount(res.account)
        m.top.getScene().callFunc("resetTo", { view: "HomeView" })
    else
        m.msg.color = "0xFF6B6BFF"
        m.msg.text = ToS(res.error)
    end if
end sub
