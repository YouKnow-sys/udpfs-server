# UDPFS Server for Android

Share folders and disk images with a PS2 over the network, straight from a phone, tablet or TV box. The PS2 side is [udpfsd](https://github.com/pcm720/udpfsd) with OPL or NHDDL.

## How to use

1. Install the apk and grant the permissions it asks for.
2. Pick a folder or a disk image (`.iso`, `.zso`, `.cso`, ...) in the config.
3. Leave *Bind IP* empty and start the server.
4. Boot the PS2 loader. The udpfs device finds the server by broadcast, no address to type in.

**Router in between:** connect the box and the PS2 to the same network and give the PS2 an address in the router subnet, e.g. `192.168.1.50` / `255.255.255.0` / gateway `192.168.1.1`.

**Direct cable:** any patch cable works. Set the box ethernet to static `192.168.1.1`, netmask `255.255.255.0`, gateway empty. The PS2 stock address is `192.168.1.10`, so nothing to change on the console.

## Building

JDK 17+, Android SDK with an NDK and Go:

```
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260819173332-ba33198847ac
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260819173332-ba33198847ac
make install
```

`make install-release` for a release build. License: GPL-3.0.
