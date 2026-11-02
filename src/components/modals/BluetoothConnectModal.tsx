import React, { useState, useEffect } from 'react';
import { obdEngine } from '../../services/obdEngine';
import { ConnectionState, AdapterInfo, RawLogPacket } from '../../types/obd';
import { 
  Bluetooth, 
  Usb, 
  CheckCircle2, 
  AlertCircle, 
  X, 
  Terminal, 
  RefreshCw, 
  Power, 
  HelpCircle,
  ExternalLink
} from 'lucide-react';

interface BluetoothConnectModalProps {
  isOpen: boolean;
  onClose: () => void;
  connectionState: ConnectionState;
  adapterInfo: AdapterInfo;
}

export const BluetoothConnectModal: React.FC<BluetoothConnectModalProps> = ({
  isOpen,
  onClose,
  connectionState,
  adapterInfo
}) => {
  const [logs, setLogs] = useState<RawLogPacket[]>([]);
  const [isConnecting, setIsConnecting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'CONNECT' | 'TERMINAL' | 'HELP'>('CONNECT');
  const [customCommand, setCustomCommand] = useState<string>('');

  useEffect(() => {
    if (!isOpen) return;
    setLogs(obdEngine.getLogs());
    const unsubLog = obdEngine.onLog((entry) => {
      setLogs((prev) => [...prev.slice(-100), entry]);
    });
    return () => unsubLog();
  }, [isOpen]);

  if (!isOpen) return null;

  const handleConnectBluetooth = async () => {
    setIsConnecting(true);
    setErrorMessage(null);
    try {
      const ok = await obdEngine.connectBluetooth();
      if (!ok) {
        setErrorMessage('Failed to connect to Bluetooth ELM327. Verify adapter is plugged in and car ignition is ON.');
      }
    } catch (e: any) {
      setErrorMessage(e.message || 'Bluetooth connection canceled or unavailable.');
    } finally {
      setIsConnecting(false);
    }
  };

  const handleConnectSerial = async () => {
    setIsConnecting(true);
    setErrorMessage(null);
    try {
      const ok = await obdEngine.connectSerial();
      if (!ok) {
        setErrorMessage('Failed to open USB Serial Port. Verify cable is securely connected.');
      }
    } catch (e: any) {
      setErrorMessage(e.message || 'Serial connection canceled.');
    } finally {
      setIsConnecting(false);
    }
  };

  const handleDisconnect = async () => {
    await obdEngine.disconnect();
  };

  const handleSendCustomCmd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customCommand.trim()) return;
    const cmd = customCommand.trim();
    setCustomCommand('');
    try {
      await obdEngine.sendCommand(cmd);
    } catch (err: any) {
      setErrorMessage(err.message);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-[#14212B] border border-slate-700 w-full max-w-2xl rounded-xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-[#0E1721]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded bg-cyan-500/20 flex items-center justify-center text-cyan-400">
              <Bluetooth className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100 font-['Chakra_Petch'] tracking-wide">
                ELM327 OBD2 ADAPTER LINK
              </h2>
              <p className="text-xs text-slate-400 font-mono">
                Real Hardware Bluetooth BLE / USB Serial Connection
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-800 bg-[#0E1721]/60 px-5 text-xs font-mono">
          <button
            onClick={() => setActiveTab('CONNECT')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors ${
              activeTab === 'CONNECT'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Connection Status
          </button>
          <button
            onClick={() => setActiveTab('TERMINAL')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
              activeTab === 'TERMINAL'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Raw AT Terminal</span>
          </button>
          <button
            onClick={() => setActiveTab('HELP')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
              activeTab === 'HELP'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Setup Guide</span>
          </button>
        </div>

        {/* Tab Content */}
        <div className="p-5 flex-1 overflow-y-auto">
          {activeTab === 'CONNECT' && (
            <div className="space-y-4">
              {/* Current Connection Status Box */}
              <div className="bg-[#0E1721] border border-slate-800 rounded-lg p-4 flex items-center justify-between">
                <div>
                  <span className="text-[11px] font-mono text-slate-400 uppercase tracking-wider block mb-1">
                    HARDWARE CONNECTION STATE
                  </span>
                  <div className="flex items-center gap-2">
                    <span 
                      className={`w-3 h-3 rounded-full ${
                        connectionState === 'CONNECTED' 
                          ? 'bg-teal-400 shadow-[0_0_8px_#52D6B0]' 
                          : connectionState === 'PAIRING' || connectionState === 'CONNECTING' || connectionState === 'INITIALIZING_ADAPTER'
                          ? 'bg-amber-400 animate-pulse'
                          : 'bg-rose-500'
                      }`} 
                    />
                    <span className="text-lg font-bold font-['Chakra_Petch'] text-slate-100">
                      {connectionState.replace(/_/g, ' ')}
                    </span>
                  </div>
                  {connectionState === 'CONNECTED' && (
                    <p className="text-xs text-teal-300 font-mono mt-1">
                      {adapterInfo.name} · {adapterInfo.elmVersion} · {adapterInfo.protocol}
                    </p>
                  )}
                </div>

                {connectionState === 'CONNECTED' ? (
                  <button
                    onClick={handleDisconnect}
                    className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-rose-300 font-semibold text-xs transition-colors"
                  >
                    <Power className="w-4 h-4" />
                    <span>Disconnect</span>
                  </button>
                ) : (
                  <div className="text-right">
                    <span className="text-xs font-mono text-slate-400 block">No Active Link</span>
                  </div>
                )}
              </div>

              {errorMessage && (
                <div className="bg-rose-950/40 border border-rose-500/40 rounded-lg p-3 text-xs text-rose-300 font-mono flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Action Buttons to Connect Real Hardware */}
              {connectionState !== 'CONNECTED' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                  <button
                    onClick={handleConnectBluetooth}
                    disabled={isConnecting}
                    className="flex flex-col items-center justify-center p-5 rounded-xl bg-gradient-to-br from-cyan-950/60 to-slate-900 border border-cyan-500/40 hover:border-cyan-400 text-cyan-300 transition-all shadow-lg group hover:scale-[1.02] disabled:opacity-50"
                  >
                    <Bluetooth className="w-8 h-8 text-cyan-400 mb-2 group-hover:scale-110 transition-transform" />
                    <span className="font-bold text-sm font-['Chakra_Petch'] text-white">
                      Connect via Bluetooth BLE
                    </span>
                    <span className="text-[11px] text-slate-400 text-center mt-1 font-mono">
                      Veepeak, Vgate iCar, Carista, OBDLink & generic ELM327 BLE
                    </span>
                  </button>

                  <button
                    onClick={handleConnectSerial}
                    disabled={isConnecting}
                    className="flex flex-col items-center justify-center p-5 rounded-xl bg-[#0E1721] border border-slate-800 hover:border-slate-700 text-slate-300 transition-all hover:scale-[1.02] disabled:opacity-50"
                  >
                    <Usb className="w-8 h-8 text-teal-400 mb-2" />
                    <span className="font-bold text-sm font-['Chakra_Petch'] text-white">
                      Connect via USB Serial
                    </span>
                    <span className="text-[11px] text-slate-400 text-center mt-1 font-mono">
                      USB OBD2 Cables (FTDI, CH340, CP2102)
                    </span>
                  </button>
                </div>
              )}

              {/* Vehicle & Protocol Specs */}
              <div className="bg-[#0E1721] rounded-lg p-3.5 border border-slate-800 text-xs font-mono space-y-1.5 text-slate-400">
                <div className="flex justify-between">
                  <span>Target Vehicle:</span>
                  <strong className="text-slate-200">Kia Sportage 2.0 MPI AWD</strong>
                </div>
                <div className="flex justify-between">
                  <span>Standard OBD Protocol:</span>
                  <strong className="text-slate-200">ISO 15765-4 (CAN 11-bit, 500 kbaud)</strong>
                </div>
                <div className="flex justify-between">
                  <span>Supported PIDs:</span>
                  <strong className="text-cyan-400">RPM, Speed, Coolant, Throttle, Load, Volts</strong>
                </div>
                <div className="flex justify-between">
                  <span>Telemetry Mode:</span>
                  <strong className="text-emerald-400">100% Real Live Hardware (Zero Simulation)</strong>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'TERMINAL' && (
            <div className="flex flex-col h-[340px] space-y-2">
              <div className="flex-1 bg-[#090F14] rounded-lg p-3 border border-slate-800 font-mono text-xs overflow-y-auto space-y-1">
                {logs.length === 0 ? (
                  <p className="text-slate-600 italic">No packet logs yet. Connect an adapter to view communication stream.</p>
                ) : (
                  logs.map((log) => (
                    <div key={log.id} className="flex items-start gap-2 leading-relaxed">
                      <span className="text-slate-600 text-[10px] shrink-0">{log.timestamp}</span>
                      <span className={`px-1 rounded text-[10px] font-bold shrink-0 ${
                        log.direction === 'TX' 
                          ? 'bg-cyan-950 text-cyan-400' 
                          : log.direction === 'RX' 
                          ? 'bg-teal-950 text-teal-400' 
                          : log.direction === 'ERR'
                          ? 'bg-rose-950 text-rose-400'
                          : 'bg-slate-800 text-slate-400'
                      }`}>
                        {log.direction}
                      </span>
                      <span className="text-slate-300 break-all">{log.data}</span>
                    </div>
                  ))
                )}
              </div>

              {/* Send Command Input Form */}
              <form onSubmit={handleSendCustomCmd} className="flex gap-2">
                <input
                  type="text"
                  value={customCommand}
                  onChange={(e) => setCustomCommand(e.target.value)}
                  placeholder="Type AT or OBD command (e.g. ATRV, 010C, 0100)..."
                  className="flex-1 bg-[#090F14] border border-slate-700 rounded px-3 py-1.5 text-xs font-mono text-slate-100 outline-none focus:border-cyan-400"
                />
                <button
                  type="submit"
                  disabled={connectionState !== 'CONNECTED'}
                  className="px-4 py-1.5 rounded bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 text-slate-950 font-bold font-mono text-xs transition-colors"
                >
                  Send
                </button>
              </form>
            </div>
          )}

          {activeTab === 'HELP' && (
            <div className="space-y-3 text-xs font-mono text-slate-300">
              <h3 className="text-sm font-bold text-cyan-400 font-['Chakra_Petch']">
                How to Connect to Your Kia Sportage
              </h3>
              <ol className="list-decimal list-inside space-y-2 text-slate-400 leading-relaxed">
                <li>
                  <strong className="text-slate-200">Plug in Adapter:</strong> Insert your ELM327 OBD2 adapter into the Kia Sportage OBD2 port (located underneath the dashboard steering column, above the foot pedals).
                </li>
                <li>
                  <strong className="text-slate-200">Turn Ignition ON:</strong> Switch car ignition key to ON position or start the engine. The power LED on the adapter should illuminate.
                </li>
                <li>
                  <strong className="text-slate-200">Tap Connect:</strong> Tap the "Connect via Bluetooth BLE" button. Your browser will prompt you to select your OBD adapter (e.g. "OBDII", "Vgate", "Veepeak").
                </li>
                <li>
                  <strong className="text-slate-200">Automatic Initialization:</strong> The app automatically executes AT handshake commands (`ATZ`, `ATE0`, `ATSP0`), detects the ECU protocol, and starts streaming live telemetry directly to your car dashboard.
                </li>
              </ol>

              <div className="bg-[#0E1721] p-3 rounded border border-slate-800 text-[11px] text-slate-400">
                <strong className="text-cyan-300 block mb-1">Browser Compatibility:</strong>
                Web Bluetooth is supported natively in Google Chrome, Microsoft Edge, and Chrome on Android.
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-5 py-3 border-t border-slate-800 bg-[#0E1721] flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-mono font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
