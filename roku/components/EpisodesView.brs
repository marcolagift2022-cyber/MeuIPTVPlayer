sub init()
    m.title = m.top.findNode("title")
    m.cover = m.top.findNode("cover")
    m.plot = m.top.findNode("plot")
    m.list = m.top.findNode("list")
    m.msg = m.top.findNode("msg")
    m.list.observeField("itemSelected", "onSelect")
end sub

sub onParams()
    p = m.top.params
    m.title.text = ToS(p.title)
    icon = ToS(p.icon)
    if icon = "" then icon = "pkg:/images/placeholder.png"
    m.cover.uri = icon
    m.msg.text = "Carregando episódios..."
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { kind: "episodes", sid: ToS(p.sid) }
    t.observeField("result", "onEpisodes")
    m.task = t
    t.control = "run"
end sub

sub doFocus()
    m.list.setFocus(true)
end sub

sub onEpisodes(ev as object)
    res = ev.getData()
    if res.ok <> true then
        m.msg.text = ToS(res.error)
        return
    end if
    c = ev.getRoSGNode().content
    m.plot.text = ToS(res.plot)
    m.list.content = c
    if c.getChildCount() = 0 then
        m.msg.text = "Esta série ainda não tem episódios."
    else
        m.msg.text = ""
    end if
    if IsActive() then m.list.setFocus(true)
end sub

sub onSelect()
    OpenItem(m.top.getScene(), m.list.content, m.list.itemSelected)
end sub
