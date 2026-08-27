package udpfsbridge

import (
	"net/netip"
	"testing"
	"time"

	"github.com/pcm720/udpfsd/server"
	"github.com/pcm720/udpfsd/udpfs"
	"github.com/pcm720/udpfsd/udprdma"
)

func TestPeerStatsFromMapsFields(t *testing.T) {
	p := server.PeerMetrics{
		Addr:     netip.MustParseAddrPort("192.168.1.10:62966"),
		LastSeen: time.Unix(1_700_000_000, 0),
		UDPFS: udpfs.Metrics{
			CommandCounts: map[udpfs.MsgType]int64{
				udpfs.MsgReadReq:   3,
				udpfs.MsgBreadReq:  2,
				udpfs.MsgWriteReq:  4,
				udpfs.MsgBwriteReq: 1,
				udpfs.MsgOpenReq:   5,
			},
			ErrorCounts: map[udpfs.MsgType]int64{
				udpfs.MsgReadReq:  1,
				udpfs.MsgWriteReq: 2,
			},
			BytesTx:         1_000,
			BytesRx:         2_000,
			AvgTxThroughput: 10.5,
			AvgRxThroughput: 20.25,
		},
		UDPRDMA: udprdma.Metrics{
			TotalPacketsTx:       100,
			TotalPacketsRx:       200,
			Retransmits:          3,
			NACKCount:            4,
			UnexpectedSeqNrCount: 5,
			PeerNACKCount:        6,
			PeerResetCount:       7,
		},
	}

	s := peerStatsFrom(p)

	if s.Addr != "192.168.1.10:62966" {
		t.Errorf("Addr = %q, want 192.168.1.10:62966", s.Addr)
	}
	if s.LastSeenUnix != 1_700_000_000 {
		t.Errorf("LastSeenUnix = %d, want 1700000000", s.LastSeenUnix)
	}
	if s.TotalOps != 15 {
		t.Errorf("TotalOps = %d, want sum of all command counts (15)", s.TotalOps)
	}
	if s.Reads != 5 {
		t.Errorf("Reads = %d, want read+bread (5)", s.Reads)
	}
	if s.Writes != 5 {
		t.Errorf("Writes = %d, want write+bwrite (5)", s.Writes)
	}
	if s.Errors != 3 {
		t.Errorf("Errors = %d, want sum of error counts (3)", s.Errors)
	}
	if s.BytesTx != 1_000 || s.BytesRx != 2_000 {
		t.Errorf("Bytes = %d/%d, want 1000/2000", s.BytesTx, s.BytesRx)
	}
	if s.AvgTxThroughput != 10.5 || s.AvgRxThroughput != 20.25 {
		t.Errorf("Throughput = %v/%v, want 10.5/20.25", s.AvgTxThroughput, s.AvgRxThroughput)
	}
	if s.PacketsTx != 100 || s.PacketsRx != 200 {
		t.Errorf("Packets = %d/%d, want 100/200", s.PacketsTx, s.PacketsRx)
	}
	if s.Retransmits != 3 || s.NackCount != 4 || s.OutOfOrder != 5 ||
		s.PeerNackCount != 6 || s.ResetCount != 7 {
		t.Errorf("UDPRDMA fields not mapped losslessly: %+v", s)
	}
}

func TestPeerStatsFromEmptyMetrics(t *testing.T) {
	s := peerStatsFrom(server.PeerMetrics{})
	if s.TotalOps != 0 || s.Errors != 0 || s.Reads != 0 || s.Writes != 0 {
		t.Errorf("empty metrics produced nonzero counts: %+v", s)
	}
}

func TestAccumulateSumsPeers(t *testing.T) {
	stats := &Stats{Running: true, UptimeSeconds: 42, PeerCount: 2}
	accumulate(stats, PeerStats{BytesTx: 1, BytesRx: 2, AvgTxThroughput: 1.5, AvgRxThroughput: 2.5,
		TotalOps: 3, Errors: 4, Reads: 5, Writes: 6, PacketsTx: 7, PacketsRx: 8,
		Retransmits: 9, NackCount: 10, OutOfOrder: 11, PeerNackCount: 12, ResetCount: 13})
	accumulate(stats, PeerStats{BytesTx: 100, BytesRx: 200, AvgTxThroughput: 10, AvgRxThroughput: 20,
		TotalOps: 30, Errors: 40, Reads: 50, Writes: 60, PacketsTx: 70, PacketsRx: 80,
		Retransmits: 90, NackCount: 100, OutOfOrder: 110, PeerNackCount: 120, ResetCount: 130})

	if stats.BytesTx != 101 || stats.BytesRx != 202 {
		t.Errorf("bytes = %d/%d, want 101/202", stats.BytesTx, stats.BytesRx)
	}
	if stats.AvgTxThroughput != 11.5 || stats.AvgRxThroughput != 22.5 {
		t.Errorf("throughput = %v/%v, want 11.5/22.5", stats.AvgTxThroughput, stats.AvgRxThroughput)
	}
	if stats.TotalOps != 33 || stats.Errors != 44 || stats.Reads != 55 || stats.Writes != 66 {
		t.Errorf("op/error counts = %d/%d/%d/%d, want 33/44/55/66",
			stats.TotalOps, stats.Errors, stats.Reads, stats.Writes)
	}
	if stats.PacketsTx != 77 || stats.PacketsRx != 88 {
		t.Errorf("packets = %d/%d, want 77/88", stats.PacketsTx, stats.PacketsRx)
	}
	if stats.Retransmits != 99 || stats.NackCount != 110 || stats.OutOfOrder != 121 ||
		stats.PeerNackCount != 132 || stats.ResetCount != 143 {
		t.Errorf("retransmit-family counts wrong: %+v", stats)
	}
	if !stats.Running || stats.UptimeSeconds != 42 || stats.PeerCount != 2 {
		t.Errorf("accumulate clobbered metadata: %+v", stats)
	}
}
