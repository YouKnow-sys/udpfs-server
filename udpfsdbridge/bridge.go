package udpfsbridge

import (
	"errors"
	"fmt"
	"log/slog"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/pcm720/udpfsd/fs"
	"github.com/pcm720/udpfsd/server"
	"github.com/pcm720/udpfsd/udpfs"
)

type ServerController struct {
	mu      sync.Mutex
	srv     *server.Server
	backend *fs.Backend
	logger  Logger
}

func NewServer() *ServerController {
	return &ServerController{}
}

func (c *ServerController) SetLogger(cb Logger) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.logger = cb
}

func (c *ServerController) loggerOrDiscard() *slog.Logger {
	if c.logger != nil {
		return slog.New(&callbackHandler{cb: c.logger})
	}
	return slog.New(slog.DiscardHandler)
}

func (c *ServerController) Start(cfg *Config) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.srv != nil {
		return errors.New("server already running")
	}
	if cfg.FSRoot == "" && cfg.BlockDevicePath == "" {
		return errors.New("set FSRoot or BlockDevicePath")
	}

	log := c.loggerOrDiscard()

	fsOpts := []fs.BackendOptFunc{
		fs.WithFSRoot(cfg.FSRoot),
		fs.WithBlockDevice(cfg.BlockDevicePath),
		fs.WithSectorSize(cfg.SectorSize),
		fs.WithCompressionCacheSize(cfg.CompressionCacheSize),
		fs.WithLogger(log),
	}
	if cfg.ReadOnly {
		fsOpts = append(fsOpts, fs.WithReadOnly())
	}
	if cfg.EnableCompression {
		fsOpts = append(fsOpts, fs.WithCompression())
	}

	backend, err := fs.NewBackend(fsOpts...)
	if err != nil {
		return fmt.Errorf("init filesystem: %w", err)
	}

	srv, err := server.New(
		server.WithDiscoveryPort(cfg.Port),
		server.WithDataIP(cfg.BindIP),
		server.WithFS(backend),
		server.WithPeerTimeout(time.Duration(cfg.PeerTimeoutMinutes)*time.Minute),
		server.WithLogger(log),
	)
	if err != nil {
		backend.Shutdown()
		return fmt.Errorf("init server: %w", err)
	}

	if err := srv.Start(); err != nil {
		backend.Shutdown()
		return fmt.Errorf("start server: %w", err)
	}

	c.srv = srv
	c.backend = backend
	return nil
}

func (c *ServerController) Stop() {
	c.mu.Lock()
	defer c.mu.Unlock()

	srv, backend := c.srv, c.backend
	if srv == nil {
		return
	}
	c.srv, c.backend = nil, nil
	srv.Close()
	backend.Shutdown()
}

func (c *ServerController) Stats() *Stats {
	c.mu.Lock()
	defer c.mu.Unlock()

	out := &Stats{}
	if c.srv == nil {
		return out
	}

	m := c.srv.Stats()
	out.Running = true
	out.UptimeSeconds = int64(m.Uptime / time.Second)
	out.PeerCount = len(m.Peers)
	for i := range m.Peers {
		out.accumulate(peerStatsFrom(m.Peers[i]))
	}
	return out
}

func (c *ServerController) Peer(i int) *PeerStats {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.srv == nil {
		return nil
	}
	m := c.srv.Stats()
	if i < 0 || i >= len(m.Peers) {
		return nil
	}
	p := peerStatsFrom(m.Peers[i])
	return &p
}

func (c *ServerController) MountInfo() *MountInfo {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.backend == nil {
		return &MountInfo{}
	}
	m := c.backend.Stats()
	return &MountInfo{
		FSRoot:       m.FSRoot,
		BlockDevice:  m.BlockDevice,
		SectorSize:   m.SectorSize,
		TotalSectors: m.TotalSectors,
		TotalBytes:   m.TotalSectors * int64(m.SectorSize),
		ReadOnly:     m.ReadOnly,
	}
}

func (c *ServerController) CompressionFormats() string {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.backend == nil {
		return ""
	}
	return strings.Join(c.backend.Stats().CompressionFormats, ",")
}

func (s *Stats) accumulate(p PeerStats) {
	s.BytesTx += p.BytesTx
	s.BytesRx += p.BytesRx
	s.AvgTxThroughput += p.AvgTxThroughput
	s.AvgRxThroughput += p.AvgRxThroughput
	s.TotalOps += p.TotalOps
	s.Errors += p.Errors
	s.Reads += p.Reads
	s.Writes += p.Writes
	s.PacketsTx += p.PacketsTx
	s.PacketsRx += p.PacketsRx
	s.Retransmits += p.Retransmits
	s.NackCount += p.NackCount
	s.OutOfOrder += p.OutOfOrder
	s.PeerNackCount += p.PeerNackCount
	s.ResetCount += p.ResetCount
}

func peerStatsFrom(p server.PeerMetrics) PeerStats {
	s := PeerStats{
		Addr:            p.Addr.String(),
		LastSeenUnix:    p.LastSeen.Unix(),
		BytesTx:         p.UDPFS.BytesTx,
		BytesRx:         p.UDPFS.BytesRx,
		AvgTxThroughput: p.UDPFS.AvgTxThroughput,
		AvgRxThroughput: p.UDPFS.AvgRxThroughput,
		PacketsTx:       int64(p.UDPRDMA.TotalPacketsTx),
		PacketsRx:       int64(p.UDPRDMA.TotalPacketsRx),
		Retransmits:     int64(p.UDPRDMA.Retransmits),
		NackCount:       int64(p.UDPRDMA.NACKCount),
		OutOfOrder:      int64(p.UDPRDMA.UnexpectedSeqNrCount),
		PeerNackCount:   int64(p.UDPRDMA.PeerNACKCount),
		ResetCount:      int64(p.UDPRDMA.PeerResetCount),
	}
	for _, n := range p.UDPFS.CommandCounts {
		s.TotalOps += n
	}
	for _, n := range p.UDPFS.ErrorCounts {
		s.Errors += n
	}
	s.Reads = p.UDPFS.CommandCounts[udpfs.MsgReadReq] + p.UDPFS.CommandCounts[udpfs.MsgBreadReq]
	s.Writes = p.UDPFS.CommandCounts[udpfs.MsgWriteReq] + p.UDPFS.CommandCounts[udpfs.MsgBwriteReq]
	return s
}

func GetLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "0.0.0.0"
	}
	for _, address := range addrs {
		if ipnet, ok := address.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ip := ipnet.IP.To4(); ip != nil {
				return ip.String()
			}
		}
	}
	return "0.0.0.0"
}
