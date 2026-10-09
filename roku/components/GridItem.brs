sub init()
    m.bg = m.top.findNode("bg")
    m.poster = m.top.findNode("poster")
    m.name = m.top.findNode("name")
    m.badgeGroup = m.top.findNode("badgeGroup")
    m.badge = m.top.findNode("badge")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    layout()
    m.name.text = c.title
    icon = c.hdposterurl
    if icon = "" then icon = "pkg:/images/placeholder.png"
    m.poster.uri = icon
    tag = c.secondarytitle
    if tag <> "" then
        m.badge.text = tag
        m.badgeGroup.visible = true
    else
        m.badgeGroup.visible = false
    end if
end sub

sub layout()
    w = m.top.width
    h = m.top.height
    if w <= 0 or h <= 0 then return
    labelH = 64
    m.bg.width = w
    m.bg.height = h
    pw = w - 16
    ph = h - labelH - 12
    m.poster.translation = [8, 8]
    m.poster.loadWidth = pw
    m.poster.loadHeight = ph
    m.poster.width = pw
    m.poster.height = ph
    m.name.translation = [6, h - labelH - 2]
    m.name.width = w - 12
    m.name.height = labelH
end sub

sub onFocus()
    if m.top.focusPercent > 0.5 then
        m.bg.color = "0x1E7A35FF"
    else
        m.bg.color = "0x141A17E6"
    end if
end sub
