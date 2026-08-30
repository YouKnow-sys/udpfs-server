# UDPFS Server for Android

<img width="1920" height="1080" alt="Server page" src="https://github.com/user-attachments/assets/2e601331-455e-4ca9-8a34-edbc94f8c453" />


Share folders and disk images with a PS2 over the network, straight from a phone, tablet or TV box. The PS2 side is [udpfsd](https://github.com/pcm720/udpfsd) with OPL or NHDDL (neutrino).

## How to use

1. Install the apk and grant the permissions it asks for.
2. Pick a folder or a disk image in the config.
3. Leave *Bind IP* empty and start the server.
4. Boot the PS2 loader. The udpfs device finds the server by broadcast, no server address to type in.

**Router in between:** connect the box and the PS2 to the same network and give the PS2 an address in the router subnet, e.g. `192.168.1.50` / `255.255.255.0` / gateway `192.168.1.1`.

**Direct cable:** any patch cable works. Set the box ethernet to static `192.168.1.1`, netmask `255.255.255.0`, gateway empty, and the PS2 to `192.168.1.10`.

The PS2 address is required. Set it as `udpfs_ip` in `nhddl.yaml` (next to the loader) or in `SYS-CONF/IPCONFIG.DAT` on the memory card (uLaunchELF: MISC → Configure → Network Settings). Neutrino 1.8.0+ reads the same address from `config/bsd-udpfs.toml`.

<details>
<summary>more screenshots</summary>
<img src="https://github.com/user-attachments/assets/49967561-7870-4620-a0c0-7cb5fc90c870" alt="Stats screen" width="1920" height="1080" />
<img src="https://github.com/user-attachments/assets/866d103a-f485-41cc-9d5b-c07fa7eed13e" alt="Config screen" width="1920" height="1080" />
</details>

## Building

JDK 17+, Android SDK with an NDK and Go:

```
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260819173332-ba33198847ac
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260819173332-ba33198847ac
make install
```

`make install-release` for a release build. License: MIT.

## Acknowledgments

Built on top of [udpfsd](https://github.com/pcm720/udpfsd) by [pcm720](https://github.com/pcm720).
