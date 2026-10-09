sub init()
    m.btn = m.top.findNode("btn")
    m.query = m.top.findNode("query")
    m.grid = m.top.findNode("grid")
    m.msg = m.top.findNode("msg")
    m.btn.observeField("buttonSelected", "openKeyboard")
    m.grid.observeField("itemSelected", "onSelect")
    m.text = ""
    m.dlg = invalid
    m.searching = false
    m.lastFocus = m.btn
    m.msg.text = "Aperte OK para digitar o nome de um canal, filme ou série."
end sub

sub onParams()
    openKeyboard()
end sub

sub doFocus()
    if m.dlg <> invalid then return
    m.lastFocus.setFocus(true)
end sub

sub openKeyboard()
    if m.searching then return
    m.kbOk = false
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = "O que você procura?"
    dlg.text = m.text
    dlg.buttons = ["Buscar", "Cancelar"]
    dlg.observeField("buttonSelected", "onKbButton")
    dlg.observeField("wasClosed", "onKbClosed")
    m.dlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onKbButton()
    if m.dlg = invalid then return
    if m.dlg.buttonSelected = 0 then
        m.text = m.dlg.text.Trim()
        m.kbOk = true
    end if
    m.dlg.close = true
end sub

sub onKbClosed()
    m.dlg = invalid
    m.btn.setFocus(true)
    m.lastFocus = m.btn
    if not m.kbOk then return
    if Len(m.text) < 2 then
        m.msg.text = "Digite pelo menos 2 letras."
        return
    end if
    runSearch()
end sub

sub runSearch()
    m.searching = true
    m.grid.content = CreateObject("roSGNode", "ContentNode")
    m.query.text = "Buscando: " + m.text
    m.msg.text = "Buscando... A primeira busca pode demorar um pouco."
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { kind: "search", query: m.text }
    t.observeField("result", "onResults")
    m.task = t
    t.control = "run"
end sub

sub onResults(ev as object)
    m.searching = false
    res = ev.getData()
    if res.ok <> true then
        m.msg.text = ToS(res.error)
        return
    end if
    c = ev.getRoSGNode().content
    m.grid.content = c
    n = c.getChildCount()
    if n = 0 then
        m.query.text = "Resultados para: " + m.text
        m.msg.text = "Nada encontrado. Tente outra palavra."
        return
    end if
    m.query.text = "Resultados para: " + m.text + "  (" + n.ToStr() + ")"
    m.msg.text = ""
    m.grid.setFocus(true)
    m.lastFocus = m.grid
end sub

sub onSelect()
    OpenItem(m.top.getScene(), m.grid.content, m.grid.itemSelected)
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "down" and m.btn.hasFocus() then
        if m.grid.content <> invalid and m.grid.content.getChildCount() > 0 then
            m.grid.setFocus(true)
            m.lastFocus = m.grid
        end if
        return true
    else if key = "up" and m.grid.hasFocus() then
        m.btn.setFocus(true)
        m.lastFocus = m.btn
        return true
    else if key = "options" and m.grid.hasFocus() then
        item = m.grid.content.getChild(m.grid.itemFocused)
        if item <> invalid then m.top.getScene().callFunc("showToast", { text: ToggleFav(item) })
        return true
    end if
    return false
end function
