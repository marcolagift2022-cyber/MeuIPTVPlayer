' X BR TOP CINE — versão Roku
' Reprodutor genérico: o usuário informa o próprio servidor Xtream Codes ou lista M3U.
sub Main(args as dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)

    ' Dados compartilhados entre as telas (conta atual e cache das listas)
    m.global = screen.getGlobalNode()
    m.global.addFields({ account: {}, cache: CreateObject("roSGNode", "Node") })

    scene = screen.CreateScene("MainScene")
    screen.show()
    scene.observeField("exitApp", port)

    while true
        msg = wait(0, port)
        msgType = type(msg)
        if msgType = "roSGScreenEvent" then
            if msg.isScreenClosed() then return
        else if msgType = "roSGNodeEvent" then
            if msg.getField() = "exitApp" and msg.getData() = true then
                screen.close()
                return
            end if
        end if
    end while
end sub
