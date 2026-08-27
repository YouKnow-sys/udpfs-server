module udpfs-android

go 1.26.4

replace github.com/pcm720/udpfsd => github.com/Youknow-sys/udpfsd v0.0.0-20260818233611-f9f035e7180a

require github.com/pcm720/udpfsd v0.0.0-00010101000000-000000000000

require (
	github.com/pierrec/lz4/v4 v4.1.27 // indirect
	golang.org/x/mobile v0.0.0-20260819173332-ba33198847ac // indirect
	golang.org/x/mod v0.39.0 // indirect
	golang.org/x/net v0.58.0 // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/sys v0.47.0 // indirect
	golang.org/x/tools v0.49.0 // indirect
)

tool golang.org/x/mobile/cmd/gobind
