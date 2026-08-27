package udpfsbridge

import "github.com/pcm720/udpfsd/udprdma"

type Logger interface {
	OnLog(level string, message string)
}

type Config struct {
	FSRoot               string
	BlockDevicePath      string
	BindIP               string
	Port                 int
	SectorSize           int
	ReadOnly             bool
	EnableCompression    bool
	CompressionCacheSize int
	PeerTimeoutMinutes   int
}

func NewDefaultConfig() *Config {
	return &Config{
		Port:                 udprdma.UDPFSPort,
		SectorSize:           512,
		CompressionCacheSize: 32,
		PeerTimeoutMinutes:   60,
	}
}

type MountInfo struct {
	FSRoot       string
	BlockDevice  string
	SectorSize   int
	TotalSectors int64
	TotalBytes   int64
	ReadOnly     bool
}

type PeerStats struct {
	Addr            string
	LastSeenUnix    int64
	BytesTx         int64
	BytesRx         int64
	AvgTxThroughput float64
	AvgRxThroughput float64
	TotalOps        int64
	Errors          int64
	Reads           int64
	Writes          int64
	PacketsTx       int64
	PacketsRx       int64
	Retransmits     int64
	NackCount       int64
	OutOfOrder      int64
	PeerNackCount   int64
	ResetCount      int64
}

type Stats struct {
	Running         bool
	UptimeSeconds   int64
	PeerCount       int
	BytesTx         int64
	BytesRx         int64
	AvgTxThroughput float64
	AvgRxThroughput float64
	TotalOps        int64
	Errors          int64
	Reads           int64
	Writes          int64
	PacketsTx       int64
	PacketsRx       int64
	Retransmits     int64
	NackCount       int64
	OutOfOrder      int64
	PeerNackCount   int64
	ResetCount      int64
}
