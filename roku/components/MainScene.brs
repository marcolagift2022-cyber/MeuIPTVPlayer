sub init()
    m.top.backgroundColor = "0x0A0F0BFF"
    m.top.backgroundUri = ""
    m.views = m.top.findNode("views")
    m.toastGroup = m.top.findNode("toastGroup")
    m.toast = m.top.findNode("toast")
    m.toastTimer = m.top.findNode("toastTimer")
    m.toastTimer.observeField("fire", "hideToast")
    m.stack = []

    acc = LoadAccount()
    if acc <> invalid then
        m.global.account = acc
        resetTo({ view: "HomeView" })
    else
        resetTo({ view: "LoginView" })
    end if
    m.top.signalBeacon("AppLaunchComplete")
end sub

' Abre uma tela nova por cima da atual: { view: "Nome", params: {...} }
function navigate(p as object) as boolean
    v = CreateObject("roSGNode", p.view)
    if v = invalid then return false
    if m.stack.Count() > 0 then m.stack.Peek().visible = false
    m.views.appendChild(v)
    m.stack.Push(v)
    params = p.params
    if params = invalid then params = {}
    v.params = params
    v.focusMe = true
    return true
end function

' Fecha a tela atual. Na primeira tela, fecha o aplicativo.
function goBack(p as object) as boolean
    if m.stack.Count() <= 1 then
        m.top.exitApp = true
        return true
    end if
    v = m.stack.Pop()
    m.views.removeChild(v)
    cur = m.stack.Peek()
    cur.visible = true
    cur.focusMe = true
    return true
end function

' Fecha todas as telas e abre outra (ex.: depois do login ou ao sair)
function resetTo(p as object) as boolean
    while m.stack.Count() > 0
        m.views.removeChild(m.stack.Pop())
    end while
    return navigate(p)
end function

function showToast(p as object) as boolean
    m.toast.text = p.text
    m.toastGroup.visible = true
    m.toastTimer.control = "stop"
    m.toastTimer.control = "start"
    return true
end function

sub hideToast()
    m.toastGroup.visible = false
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if press and key = "back" then return goBack({})
    return false
end function
