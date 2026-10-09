sub init()
    m.bg = m.top.findNode("bg")
    m.title = m.top.findNode("title")
    m.sub = m.top.findNode("sub")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    m.title.text = c.title
    m.sub.text = c.description
    layout()
end sub

sub layout()
    w = m.top.width
    h = m.top.height
    if w <= 0 or h <= 0 then return
    m.bg.width = w
    m.bg.height = h
    m.title.translation = [0, h * 0.22]
    m.title.width = w
    m.title.height = h * 0.4
    m.sub.translation = [0, h * 0.6]
    m.sub.width = w
    m.sub.height = h * 0.25
end sub

sub onFocus()
    if m.top.focusPercent > 0.5 then
        m.bg.color = "0x1E7A35FF"
    else
        m.bg.color = "0x141A17E6"
    end if
end sub
