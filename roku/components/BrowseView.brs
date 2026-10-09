sub init()
    m.title = m.top.findNode("title")
    m.count = m.top.findNode("count")
    m.cats = m.top.findNode("cats")
    m.grid = m.top.findNode("grid")
    m.msg = m.top.findNode("msg")
    m.catTimer = m.top.findNode("catTimer")

    m.cats.observeField("itemFocused", "onCatFocused")
    m.cats.observeField("itemSelected", "onCatSelected")
    m.grid.observeField("itemSelected", "onItemSelected")
    m.catTimer.observeField("fire", "loadFocusedCat")

    m.type = ""
    m.curCat = ""
    m.focusGrid = false
    m.lastFocus = m.cats
end sub

sub onParams()
    m.type = ToS(m.top.params.type)
    titles = { live: "TV ao vivo", movie: "Filmes", series: "Séries", fav: "Favoritos" }
    m.title.text = titles[m.type]
    if m.type = "live" then
        m.grid.itemSize = [240, 190]
        m.grid.itemSpacing = [24, 24]
        m.grid.numColumns = 5
        m.grid.numRows = 4
    else
        m.grid.itemSize = [200, 380]
        m.grid.itemSpacing = [22, 30]
        m.grid.numColumns = 6
        m.grid.numRows = 2
    end if
    loadCats()
end sub

sub doFocus()
    m.lastFocus.setFocus(true)
end sub

' ---------------------------------------------------------------- categorias
sub loadCats()
    if m.type = "fav" then
        c = CreateObject("roSGNode", "ContentNode")
        for each p in [["Todos", "all"], ["TV ao vivo", "live"], ["Filmes", "movie"], ["Séries", "series"]]
            n = c.createChild("ContentNode")
            n.title = p[0]
            n.shortdescriptionline2 = p[1]
        end for
        setCats(c)
        return
    end if
    m.msg.text = "Carregando categorias..."
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { kind: "cats", type: m.type }
    t.observeField("result", "onCats")
    m.catTask = t
    t.control = "run"
end sub

sub onCats(ev as object)
    res = ev.getData()
    if res.ok <> true then
        m.msg.text = ToS(res.error)
        return
    end if
    setCats(ev.getRoSGNode().content)
end sub

sub setCats(c as object)
    m.cats.content = c
    m.msg.text = ""
    m.curCat = ""
    loadItems(0)
end sub

sub onCatFocused()
    m.catTimer.control = "stop"
    m.catTimer.control = "start"
end sub

sub loadFocusedCat()
    loadItems(m.cats.itemFocused)
end sub

sub onCatSelected()
    m.focusGrid = true
    loadItems(m.cats.itemSelected)
end sub

' ---------------------------------------------------------------- itens
sub loadItems(idx as integer)
    if m.cats.content = invalid then return
    node = m.cats.content.getChild(idx)
    if node = invalid then return
    cat = node.shortdescriptionline2
    if cat = m.curCat then
        if m.focusGrid then focusGridIfFilled()
        return
    end if
    m.curCat = cat
    m.count.text = ""

    if m.type = "fav" then
        showItems(FavsToContent(cat))
        return
    end if

    m.grid.content = CreateObject("roSGNode", "ContentNode")
    m.msg.text = "Carregando..."
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { kind: "items", type: m.type, cat: cat }
    t.observeField("result", "onItems")
    m.itemTask = t
    t.control = "run"
end sub

sub onItems(ev as object)
    t = ev.getRoSGNode()
    if ToS(t.request.cat) <> m.curCat then return
    res = ev.getData()
    if res.ok <> true then
        m.msg.text = ToS(res.error)
        return
    end if
    showItems(t.content)
end sub

sub showItems(c as object)
    m.grid.content = c
    n = c.getChildCount()
    if n = 0 then
        if m.type = "fav" then
            m.msg.text = "Nenhum favorito aqui. Na lista, aperte * (asterisco) em um item para favoritar."
        else
            m.msg.text = "Nada nesta categoria."
        end if
        m.count.text = ""
        if m.grid.hasFocus() and IsActive() then
            m.cats.setFocus(true)
            m.lastFocus = m.cats
        end if
    else
        m.msg.text = ""
        if n = 1 then
            m.count.text = "1 item"
        else
            m.count.text = n.ToStr() + " itens"
        end if
    end if
    if m.focusGrid then focusGridIfFilled()
end sub

sub focusGridIfFilled()
    m.focusGrid = false
    if not IsActive() then return
    if m.grid.content <> invalid and m.grid.content.getChildCount() > 0 then
        m.grid.setFocus(true)
        m.lastFocus = m.grid
    end if
end sub

sub onItemSelected()
    OpenItem(m.top.getScene(), m.grid.content, m.grid.itemSelected)
end sub

sub toggleFocusedFav()
    item = m.grid.content.getChild(m.grid.itemFocused)
    if item = invalid then return
    msg = ToggleFav(item)
    m.top.getScene().callFunc("showToast", { text: msg })
    if m.type = "fav" then
        m.curCat = ""
        loadItems(m.cats.itemFocused)
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "right" and m.cats.hasFocus() then
        ' Se a categoria ainda não carregou, carrega agora e entra na grade quando chegar
        m.catTimer.control = "stop"
        node = invalid
        if m.cats.content <> invalid then node = m.cats.content.getChild(m.cats.itemFocused)
        if node <> invalid then
            if node.shortdescriptionline2 <> m.curCat then
                m.focusGrid = true
                loadItems(m.cats.itemFocused)
                return true
            end if
        end if
        if m.grid.content <> invalid and m.grid.content.getChildCount() > 0 then
            m.grid.setFocus(true)
            m.lastFocus = m.grid
        else
            m.focusGrid = true
        end if
        return true
    else if key = "left" and m.grid.hasFocus() then
        m.cats.setFocus(true)
        m.lastFocus = m.cats
        return true
    else if key = "options" and m.grid.hasFocus() then
        toggleFocusedFav()
        return true
    end if
    return false
end function
