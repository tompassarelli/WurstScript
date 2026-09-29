function config takes nothing returns nothing
    call SetPlayers(1)
    call SetTeams(1)
    call DefineStartLocation(0, 0., 0.)
    call SetPlayerController(Player(0), MAP_CONTROL_USER)
endfunction
function main takes nothing returns nothing
    call InitBlizzard()
endfunction
