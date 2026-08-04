"use client";

import React, { useState, useEffect } from "react";

interface DeviceItem {
  deviceId: string;
  userId: string | null;
  displayName: string | null;
  username: string | null;
  profileImage: string | null;
  appVersion: string;
  mobileNumber: string | null;
  isBlocked: boolean;
  eunoiaDisabled: boolean;
  lastSeen: number;
}

interface BlockedNumberItem {
  mobileNumber: string;
  createdAt: number;
}

interface DetailedDeviceData extends DeviceItem {
  fcmToken?: string | null;
  stats?: {
    likedCount: number;
    playlistCount: number;
    recentActivity: Array<{
      videoId: string;
      title: string;
      author: string;
      action: string;
      timestamp: number;
    }>;
  };
}

export default function AdminDashboardPage() {
  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [blockedNumbers, setBlockedNumbers] = useState<BlockedNumberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [blockMobileInput, setBlockMobileInput] = useState("");
  const [statusMsg, setStatusMsg] = useState<string | null>(null);

  // Inspector Modal State
  const [selectedDevice, setSelectedDevice] = useState<DetailedDeviceData | null>(null);
  const [inspectorLoading, setInspectorLoading] = useState(false);

  const fetchAdminData = async () => {
    try {
      setLoading(true);
      const res = await fetch("/api/admin/devices");
      const data = await res.json();
      if (data.success) {
        setDevices(data.devices || []);
        setBlockedNumbers(data.blockedNumbers || []);
      }
    } catch (err: any) {
      console.error("Failed to load admin data:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdminData();
  }, []);

  const handleInspectDevice = async (deviceId: string) => {
    try {
      setInspectorLoading(true);
      setSelectedDevice(null);
      const res = await fetch(`/api/admin/devices?deviceId=${encodeURIComponent(deviceId)}`);
      const data = await res.json();
      if (data.success && data.device) {
        setSelectedDevice(data.device);
      } else {
        alert("Could not load device details: " + (data.error || "Unknown error"));
      }
    } catch (err: any) {
      alert("Failed to inspect device: " + err.message);
    } finally {
      setInspectorLoading(false);
    }
  };

  const handleDeviceAction = async (action: string, deviceId: string, mobileNumber?: string | null) => {
    try {
      const res = await fetch("/api/admin/devices", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action,
          deviceId,
          mobileNumber,
          eunoiaDisabled: action === "toggle_eunoia" ? !devices.find(d => d.deviceId === deviceId)?.eunoiaDisabled : false
        })
      });
      const data = await res.json();
      if (data.success) {
        setStatusMsg(`✅ Security action completed successfully!`);
        setTimeout(() => setStatusMsg(null), 3000);
        fetchAdminData();
        if (selectedDevice && selectedDevice.deviceId === deviceId) {
          handleInspectDevice(deviceId);
        }
      } else {
        alert("Action failed: " + (data.error || "Unknown error"));
      }
    } catch (err: any) {
      alert("Network error: " + err.message);
    }
  };

  const handleBlockMobileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!blockMobileInput.trim()) return;
    try {
      const res = await fetch("/api/admin/devices", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: "block_mobile",
          mobileNumber: blockMobileInput.trim()
        })
      });
      const data = await res.json();
      if (data.success) {
        setStatusMsg(`🚨 Mobile Number ${blockMobileInput} blocked!`);
        setBlockMobileInput("");
        setTimeout(() => setStatusMsg(null), 3000);
        fetchAdminData();
      } else {
        alert("Failed to block mobile: " + (data.error || "Unknown error"));
      }
    } catch (err: any) {
      alert("Error: " + err.message);
    }
  };

  const handleSetMobilePrompt = (deviceId: string, currentMobile: string | null) => {
    const newMobile = prompt("Enter Mobile Number for device " + deviceId + ":", currentMobile || "");
    if (newMobile !== null && newMobile.trim() !== "") {
      handleDeviceAction("set_mobile", deviceId, newMobile.trim());
    }
  };

  const filteredDevices = devices.filter(d => 
    d.deviceId.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (d.displayName && d.displayName.toLowerCase().includes(searchTerm.toLowerCase())) ||
    (d.mobileNumber && d.mobileNumber.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "#0A0C10", color: "#F3F4F6", fontFamily: "system-ui, -apple-system, sans-serif", padding: "24px" }}>
      <div style={{ maxWidth: "1280px", margin: "0 auto" }}>
        {/* Header */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px", borderBottom: "1px solid #1F2937", paddingBottom: "16px" }}>
          <div>
            <h1 style={{ fontSize: "28px", fontWeight: "bold", color: "#10B981", margin: 0 }}>🛡️ Spasiba Admin Security Control</h1>
            <p style={{ color: "#9CA3AF", fontSize: "14px", marginTop: "4px" }}>Live User Access, SIM Mobile Detection & Device Inspection</p>
          </div>
          <button 
            onClick={fetchAdminData}
            style={{ backgroundColor: "#10B981", color: "#000", border: "none", padding: "10px 18px", borderRadius: "8px", fontWeight: "bold", cursor: "pointer" }}
          >
            🔄 Refresh DB
          </button>
        </div>

        {statusMsg && (
          <div style={{ backgroundColor: "#064E3B", color: "#34D399", padding: "12px 16px", borderRadius: "8px", marginBottom: "20px", border: "1px solid #059669", fontWeight: "bold" }}>
            {statusMsg}
          </div>
        )}

        {/* Stats Row */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "16px", marginBottom: "24px" }}>
          <div style={{ backgroundColor: "#161B22", padding: "20px", borderRadius: "12px", border: "1px solid #30363D" }}>
            <div style={{ color: "#9CA3AF", fontSize: "13px" }}>Total Devices Registered</div>
            <div style={{ fontSize: "28px", fontWeight: "bold", color: "#FFF", marginTop: "4px" }}>{devices.length}</div>
          </div>
          <div style={{ backgroundColor: "#161B22", padding: "20px", borderRadius: "12px", border: "1px solid #EF4444" }}>
            <div style={{ color: "#EF4444", fontSize: "13px" }}>Blocked Devices</div>
            <div style={{ fontSize: "28px", fontWeight: "bold", color: "#EF4444", marginTop: "4px" }}>{devices.filter(d => d.isBlocked).length}</div>
          </div>
          <div style={{ backgroundColor: "#161B22", padding: "20px", borderRadius: "12px", border: "1px solid #34D399" }}>
            <div style={{ color: "#34D399", fontSize: "13px" }}>Auto-Detected SIM Numbers</div>
            <div style={{ fontSize: "28px", fontWeight: "bold", color: "#34D399", marginTop: "4px" }}>{devices.filter(d => d.mobileNumber).length}</div>
          </div>
          <div style={{ backgroundColor: "#161B22", padding: "20px", borderRadius: "12px", border: "1px solid #30363D" }}>
            <div style={{ color: "#9CA3AF", fontSize: "13px" }}>Flagged Mobile Numbers</div>
            <div style={{ fontSize: "28px", fontWeight: "bold", color: "#FFF", marginTop: "4px" }}>{blockedNumbers.length}</div>
          </div>
        </div>

        {/* Quick Block Mobile Form */}
        <div style={{ backgroundColor: "#161B22", padding: "20px", borderRadius: "12px", border: "1px solid #30363D", marginBottom: "24px" }}>
          <h2 style={{ fontSize: "18px", fontWeight: "bold", color: "#EF4444", marginTop: 0, marginBottom: "12px" }}>🚨 Quick Mobile Number Block</h2>
          <form onSubmit={handleBlockMobileSubmit} style={{ display: "flex", gap: "12px" }}>
            <input 
              type="text" 
              placeholder="Enter Mobile Number (e.g. +91 9876543210)"
              value={blockMobileInput}
              onChange={(e) => setBlockMobileInput(e.target.value)}
              style={{ flex: 1, padding: "10px 14px", backgroundColor: "#0D1117", border: "1px solid #30363D", borderRadius: "8px", color: "#FFF" }}
            />
            <button 
              type="submit"
              style={{ backgroundColor: "#EF4444", color: "#FFF", border: "none", padding: "10px 20px", borderRadius: "8px", fontWeight: "bold", cursor: "pointer" }}
            >
              ⛔ Block Mobile & Associated Devices
            </button>
          </form>
        </div>

        {/* Search Input */}
        <div style={{ marginBottom: "16px" }}>
          <input 
            type="text" 
            placeholder="🔍 Search by Device ID, User Name, or Mobile Number (Click any row to inspect)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ width: "100%", padding: "12px 16px", backgroundColor: "#161B22", border: "1px solid #30363D", borderRadius: "10px", color: "#FFF", fontSize: "14px" }}
          />
        </div>

        {/* Devices Table */}
        <div style={{ backgroundColor: "#161B22", borderRadius: "12px", border: "1px solid #30363D", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
            <thead>
              <tr style={{ backgroundColor: "#21262D", color: "#9CA3AF", borderBottom: "1px solid #30363D" }}>
                <th style={{ padding: "14px 16px" }}>User & Photo</th>
                <th style={{ padding: "14px 16px" }}>Device ID</th>
                <th style={{ padding: "14px 16px" }}>SIM Mobile Number</th>
                <th style={{ padding: "14px 16px" }}>Security Status</th>
                <th style={{ padding: "14px 16px" }}>Last Active</th>
                <th style={{ padding: "14px 16px", textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} style={{ padding: "24px", textAlign: "center", color: "#9CA3AF" }}>Loading database records...</td>
                </tr>
              ) : filteredDevices.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ padding: "24px", textAlign: "center", color: "#9CA3AF" }}>No registered devices match your filter.</td>
                </tr>
              ) : (
                filteredDevices.map((d) => (
                  <tr 
                    key={d.deviceId} 
                    onClick={() => handleInspectDevice(d.deviceId)}
                    style={{ 
                      borderBottom: "1px solid #21262D", 
                      backgroundColor: d.isBlocked ? "rgba(239, 68, 68, 0.08)" : "transparent",
                      cursor: "pointer"
                    }}
                  >
                    <td style={{ padding: "14px 16px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        {d.profileImage ? (
                          <img src={d.profileImage} alt="Avatar" style={{ width: "40px", height: "40px", borderRadius: "50%", objectFit: "cover", border: "1px solid #30363D" }} />
                        ) : (
                          <div style={{ width: "40px", height: "40px", borderRadius: "50%", backgroundColor: "#1F2937", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "16px", fontWeight: "bold", color: "#10B981" }}>
                            {(d.displayName || d.username || "U")[0].toUpperCase()}
                          </div>
                        )}
                        <div>
                          <div style={{ fontWeight: "bold", color: "#FFF" }}>{d.displayName || d.username || "Anonymous User"}</div>
                          <div style={{ fontSize: "12px", color: "#9CA3AF" }}>@{d.username || "no_username"}</div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: "14px 16px", fontFamily: "monospace", color: "#60A5FA", fontWeight: "bold" }}>
                      {d.deviceId}
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      {d.mobileNumber ? (
                        <span style={{ color: "#34D399", fontWeight: "bold" }}>📱 {d.mobileNumber}</span>
                      ) : (
                        <button 
                          onClick={(e) => { e.stopPropagation(); handleSetMobilePrompt(d.deviceId, d.mobileNumber); }}
                          style={{ backgroundColor: "transparent", color: "#60A5FA", border: "1px dashed #3B82F6", padding: "4px 8px", borderRadius: "6px", fontSize: "12px", cursor: "pointer" }}
                        >
                          + Add Mobile
                        </button>
                      )}
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      {d.isBlocked ? (
                        <span style={{ backgroundColor: "#DC2626", color: "#FFF", padding: "4px 8px", borderRadius: "6px", fontSize: "12px", fontWeight: "bold" }}>⛔ BLOCKED</span>
                      ) : (
                        <span style={{ backgroundColor: "#047857", color: "#FFF", padding: "4px 8px", borderRadius: "6px", fontSize: "12px", fontWeight: "bold" }}>ACTIVE</span>
                      )}
                    </td>
                    <td style={{ padding: "14px 16px", color: "#9CA3AF", fontSize: "12px" }}>
                      {new Date(d.lastSeen).toLocaleString()}
                    </td>
                    <td style={{ padding: "14px 16px", textAlign: "right" }}>
                      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }} onClick={(e) => e.stopPropagation()}>
                        <button 
                          onClick={() => handleInspectDevice(d.deviceId)}
                          style={{ backgroundColor: "#3B82F6", color: "#FFF", border: "none", padding: "6px 12px", borderRadius: "6px", fontSize: "12px", fontWeight: "bold", cursor: "pointer" }}
                        >
                          🔍 Inspect
                        </button>
                        {d.isBlocked ? (
                          <button 
                            onClick={() => handleDeviceAction("unblock", d.deviceId, d.mobileNumber)}
                            style={{ backgroundColor: "#059669", color: "#FFF", border: "none", padding: "6px 12px", borderRadius: "6px", fontSize: "12px", fontWeight: "bold", cursor: "pointer" }}
                          >
                            🟢 Unblock
                          </button>
                        ) : (
                          <button 
                            onClick={() => handleDeviceAction("block", d.deviceId, d.mobileNumber)}
                            style={{ backgroundColor: "#DC2626", color: "#FFF", border: "none", padding: "6px 12px", borderRadius: "6px", fontSize: "12px", fontWeight: "bold", cursor: "pointer" }}
                          >
                            ⛔ Block
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Deep Device Inspector Modal */}
      {(selectedDevice || inspectorLoading) && (
        <div style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0, 0, 0, 0.75)", backdropFilter: "blur(6px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999, padding: "20px" }}>
          <div style={{ backgroundColor: "#161B22", border: "1px solid #30363D", borderRadius: "16px", width: "100%", maxWidth: "640px", maxHeight: "90vh", overflowY: "auto", padding: "24px", color: "#FFF", boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.5)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px", borderBottom: "1px solid #30363D", paddingBottom: "12px" }}>
              <h2 style={{ fontSize: "20px", fontWeight: "bold", color: "#10B981", margin: 0 }}>🔍 Deep Device & User Inspector</h2>
              <button 
                onClick={() => setSelectedDevice(null)}
                style={{ backgroundColor: "transparent", border: "none", color: "#9CA3AF", fontSize: "24px", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            {inspectorLoading ? (
              <div style={{ padding: "40px", textAlign: "center", color: "#9CA3AF" }}>Fetching full user details and activity logs...</div>
            ) : selectedDevice ? (
              <div>
                {/* Profile Card */}
                <div style={{ display: "flex", alignItems: "center", gap: "16px", backgroundColor: "#0D1117", padding: "16px", borderRadius: "12px", border: "1px solid #30363D", marginBottom: "20px" }}>
                  {selectedDevice.profileImage ? (
                    <img src={selectedDevice.profileImage} alt="User Avatar" style={{ width: "64px", height: "64px", borderRadius: "50%", objectFit: "cover", border: "2px solid #10B981" }} />
                  ) : (
                    <div style={{ width: "64px", height: "64px", borderRadius: "50%", backgroundColor: "#10B981", color: "#000", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "26px", fontWeight: "bold" }}>
                      {(selectedDevice.displayName || selectedDevice.username || "U")[0].toUpperCase()}
                    </div>
                  )}
                  <div>
                    <h3 style={{ margin: 0, fontSize: "18px", fontWeight: "bold", color: "#FFF" }}>{selectedDevice.displayName || selectedDevice.username || "Anonymous User"}</h3>
                    <div style={{ color: "#9CA3AF", fontSize: "13px" }}>@{selectedDevice.username || "no_username"}</div>
                    <div style={{ marginTop: "4px", fontSize: "12px", color: "#34D399", fontWeight: "bold" }}>
                      📱 {selectedDevice.mobileNumber ? selectedDevice.mobileNumber : "No SIM Mobile Registered"}
                    </div>
                  </div>
                </div>

                {/* Device Meta Info */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "20px" }}>
                  <div style={{ backgroundColor: "#0D1117", padding: "12px", borderRadius: "8px", border: "1px solid #21262D" }}>
                    <div style={{ color: "#9CA3AF", fontSize: "11px" }}>DEVICE ID</div>
                    <div style={{ fontSize: "13px", fontWeight: "bold", fontFamily: "monospace", color: "#60A5FA", marginTop: "2px" }}>{selectedDevice.deviceId}</div>
                  </div>
                  <div style={{ backgroundColor: "#0D1117", padding: "12px", borderRadius: "8px", border: "1px solid #21262D" }}>
                    <div style={{ color: "#9CA3AF", fontSize: "11px" }}>APP VERSION</div>
                    <div style={{ fontSize: "13px", fontWeight: "bold", color: "#FFF", marginTop: "2px" }}>{selectedDevice.appVersion}</div>
                  </div>
                  <div style={{ backgroundColor: "#0D1117", padding: "12px", borderRadius: "8px", border: "1px solid #21262D" }}>
                    <div style={{ color: "#9CA3AF", fontSize: "11px" }}>LIKED SONGS</div>
                    <div style={{ fontSize: "16px", fontWeight: "bold", color: "#EC4899", marginTop: "2px" }}>{selectedDevice.stats?.likedCount || 0} tracks</div>
                  </div>
                  <div style={{ backgroundColor: "#0D1117", padding: "12px", borderRadius: "8px", border: "1px solid #21262D" }}>
                    <div style={{ color: "#9CA3AF", fontSize: "11px" }}>PLAYLISTS CREATED</div>
                    <div style={{ fontSize: "16px", fontWeight: "bold", color: "#3B82F6", marginTop: "2px" }}>{selectedDevice.stats?.playlistCount || 0} playlists</div>
                  </div>
                </div>

                {/* FCM Token Details */}
                <div style={{ backgroundColor: "#0D1117", padding: "12px", borderRadius: "8px", border: "1px solid #21262D", marginBottom: "20px" }}>
                  <div style={{ color: "#9CA3AF", fontSize: "11px" }}>FCM PUSH TOKEN</div>
                  <div style={{ fontSize: "11px", fontFamily: "monospace", color: "#D1D5DB", wordBreak: "break-all", marginTop: "2px" }}>
                    {selectedDevice.fcmToken || "No Active Push Token Registered"}
                  </div>
                </div>

                {/* Recent Activity Log */}
                <div style={{ marginBottom: "20px" }}>
                  <h4 style={{ fontSize: "14px", fontWeight: "bold", color: "#10B981", marginBottom: "10px" }}>🎵 Recent Listening Activity</h4>
                  <div style={{ backgroundColor: "#0D1117", borderRadius: "8px", border: "1px solid #21262D", overflow: "hidden" }}>
                    {selectedDevice.stats?.recentActivity && selectedDevice.stats.recentActivity.length > 0 ? (
                      selectedDevice.stats.recentActivity.map((act, idx) => (
                        <div key={idx} style={{ padding: "10px 12px", borderBottom: "1px solid #161B22", display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "12px" }}>
                          <div>
                            <div style={{ fontWeight: "bold", color: "#FFF" }}>{act.title}</div>
                            <div style={{ color: "#9CA3AF", fontSize: "11px" }}>{act.author} • <span style={{ color: "#34D399" }}>{act.action}</span></div>
                          </div>
                          <div style={{ color: "#6B7280", fontSize: "10px" }}>{new Date(act.timestamp).toLocaleTimeString()}</div>
                        </div>
                      ))
                    ) : (
                      <div style={{ padding: "16px", textAlign: "center", color: "#6B7280", fontSize: "12px" }}>No recent stream events recorded for this device.</div>
                    )}
                  </div>
                </div>

                {/* Quick Security Controls inside Modal */}
                <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end", borderTop: "1px solid #30363D", paddingTop: "16px" }}>
                  <button 
                    onClick={() => handleDeviceAction("toggle_eunoia", selectedDevice.deviceId)}
                    style={{ backgroundColor: "#374151", color: "#FFF", border: "none", padding: "8px 16px", borderRadius: "8px", fontSize: "13px", fontWeight: "bold", cursor: "pointer" }}
                  >
                    {selectedDevice.eunoiaDisabled ? "🔓 Grant Eunoia Access" : "🔒 Revoke Eunoia Access"}
                  </button>
                  {selectedDevice.isBlocked ? (
                    <button 
                      onClick={() => handleDeviceAction("unblock", selectedDevice.deviceId, selectedDevice.mobileNumber)}
                      style={{ backgroundColor: "#059669", color: "#FFF", border: "none", padding: "8px 16px", borderRadius: "8px", fontSize: "13px", fontWeight: "bold", cursor: "pointer" }}
                    >
                      🟢 Unblock User & SIM
                    </button>
                  ) : (
                    <button 
                      onClick={() => handleDeviceAction("block", selectedDevice.deviceId, selectedDevice.mobileNumber)}
                      style={{ backgroundColor: "#DC2626", color: "#FFF", border: "none", padding: "8px 16px", borderRadius: "8px", fontSize: "13px", fontWeight: "bold", cursor: "pointer" }}
                    >
                      ⛔ Block Device & SIM
                    </button>
                  )}
                </div>
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}
