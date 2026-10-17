package main

import (
	"net"
	"strings"
	"testing"
)

func TestConfigurationDenialsPreserveDeviceReason(t *testing.T) {
	for _, raw := range []bool{false, true} {
		for reason, marker := range map[string]string{
			"device_mismatch": "DEVICE_ACCESS", "device_limit": "DEVICE_LIMIT",
			"wrong_password": "неверный пароль", "expired": "истёк",
		} {
			t.Run(reason+map[bool]string{false: "/wg", true: "/raw"}[raw], func(t *testing.T) {
				client, server := net.Pipe()
				defer client.Close()
				go func() {
					defer server.Close()
					buf := make([]byte, 4096)
					if _, err := server.Read(buf); err == nil {
						_, _ = server.Write([]byte("DENIED:" + reason))
					}
				}()
				var err error
				if raw {
					_, _, _, err = RequestRawConfig(client, "test-device", "synthetic")
				} else {
					_, err = RequestConfig(client, "9000", "test-device", "synthetic")
				}
				if err == nil || !strings.Contains(err.Error(), marker) {
					t.Fatalf("expected %q, got %v", marker, err)
				}
				if strings.HasPrefix(reason, "device_") && strings.Contains(workerErrorHint(err), "неверный пароль") {
					t.Fatal("device denial was presented as wrong password")
				}
			})
		}
	}
}
