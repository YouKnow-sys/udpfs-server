package udpfsbridge

import (
	"context"
	"log/slog"
)

type callbackHandler struct {
	cb Logger
}

func (h *callbackHandler) Enabled(_ context.Context, l slog.Level) bool { return l >= slog.LevelInfo }

func (h *callbackHandler) Handle(_ context.Context, r slog.Record) error {
	h.cb.OnLog(r.Level.String(), r.Message)
	return nil
}

func (h *callbackHandler) WithAttrs([]slog.Attr) slog.Handler { return h }
func (h *callbackHandler) WithGroup(string) slog.Handler      { return h }
