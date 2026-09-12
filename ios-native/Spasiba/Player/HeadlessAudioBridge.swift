import Foundation
import WebKit
import AVFoundation
import MediaPlayer
import Combine
import SwiftUI

public final class HeadlessAudioBridge: NSObject, WKScriptMessageHandler, WKNavigationDelegate, ObservableObject {
    public static let shared = HeadlessAudioBridge()

    private var webView: WKWebView?
    private var isReady: Bool = false
    private var pendingVideoId: String? = nil
    private var userWantsPlayback: Bool = false

    // Callbacks for NativePlayerManager
    public var onStateChange: ((_ isPlaying: Bool, _ isBuffering: Bool) -> Void)?
    public var onTimeUpdate: ((_ currentTime: Double, _ duration: Double) -> Void)?
    public var onTrackEnded: (() -> Void)?
    public var onError: ((_ errorCode: Int) -> Void)?

    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var isBuffering: Bool = false
    @Published public private(set) var currentTime: Double = 0.0
    @Published public private(set) var duration: Double = 0.0

    private override init() {
        super.init()
        setupAudioSession()
    }

    // MARK: - Audio Session Configuration
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, policy: .longFormAudio, options: [])
            try session.setActive(true)
            NSLog("[HeadlessAudioBridge] AVAudioSession configured for longFormAudio .playback")
        } catch {
            NSLog("[HeadlessAudioBridge] Failed to configure AVAudioSession: %@", error.localizedDescription)
        }

        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            if self.userWantsPlayback {
                self.webView?.evaluateJavaScript("if (player && player.getPlayerState && player.getPlayerState() !== 1) { player.playVideo(); }", completionHandler: nil)
            }
        }
    }

    // MARK: - WKWebView Setup
    public func getOrCreateWebView() -> WKWebView {
        if let existing = self.webView {
            return existing
        }
        let configuration = createConfiguration()
        let wv = WKWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 240), configuration: configuration)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.backgroundColor = .clear
        wv.customUserAgent = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        wv.navigationDelegate = self
        self.webView = wv
        self.loadPlayerHTML()
        NSLog("[HeadlessAudioBridge] Successfully created Headless WKWebView")
        return wv
    }

    private func createConfiguration() -> WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsAirPlayForMediaPlayback = true
        configuration.allowsPictureInPictureMediaPlayback = true
        if #available(iOS 15.4, *) {
            configuration.preferences.isElementFullscreenEnabled = true
        }

        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = true
        configuration.defaultWebpagePreferences = preferences

        let userContentController = WKUserContentController()
        userContentController.add(self, name: "spasibaBridge")

        let webkitHiderSource = """
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.spasibaBridge) {
            window.__spasibaBridge = window.webkit.messageHandlers.spasibaBridge;
        }
        try {
            Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
            Object.defineProperty(Document.prototype, 'hidden', { get: function() { return false; }, configurable: true });
            Object.defineProperty(Document.prototype, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
        } catch (e) {}

        var blockVisEvent = function(e) { e.stopImmediatePropagation(); };
        window.addEventListener('visibilitychange', blockVisEvent, true);
        document.addEventListener('visibilitychange', blockVisEvent, true);
        window.addEventListener('blur', blockVisEvent, true);
        window.addEventListener('pagehide', blockVisEvent, true);

        try {
            if (navigator.audioSession) {
                navigator.audioSession.type = 'playback';
            }
        } catch (e) {}
        """
        let webkitHiderScript = WKUserScript(
            source: webkitHiderSource,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        userContentController.addUserScript(webkitHiderScript)
        configuration.userContentController = userContentController
        return configuration
    }

    public func attachToWindow() {
        // Handled cleanly by HeadlessAudioBridgeRepresentable in SwiftUI hierarchy
    }

    private func loadPlayerHTML() {
        let html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="always">
          <script>
            try {
              Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
              Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
              Object.defineProperty(Document.prototype, 'hidden', { get: function() { return false; }, configurable: true });
              Object.defineProperty(Document.prototype, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
            } catch (e) {}

            var blockVisEvent = function(e) { e.stopImmediatePropagation(); };
            window.addEventListener('visibilitychange', blockVisEvent, true);
            document.addEventListener('visibilitychange', blockVisEvent, true);
            window.addEventListener('blur', blockVisEvent, true);
            window.addEventListener('pagehide', blockVisEvent, true);

            try {
              if (navigator.audioSession) {
                navigator.audioSession.type = 'playback';
              }
            } catch (e) {}
          </script>
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background-color:#000; }
            #player { width:100%; height:100%; position:absolute; top:0; left:0; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            function postBridge(data) {
              if (window.__spasibaBridge) {
                window.__spasibaBridge.postMessage(data);
              } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.spasibaBridge) {
                window.webkit.messageHandlers.spasibaBridge.postMessage(data);
              }
            }

            window.onerror = function(msg, url, lineNo, columnNo, error) {
              postBridge({ type: 'log', message: 'WINDOW ERROR: ' + msg + ' at line ' + lineNo });
              return false;
            };

            var origLog = console.log;
            console.log = function() {
              origLog.apply(console, arguments);
              var args = Array.prototype.slice.call(arguments).join(' ');
              postBridge({ type: 'log', message: '[Console.log] ' + args });
            };

            var origWarn = console.warn;
            console.warn = function() {
              origWarn.apply(console, arguments);
              var args = Array.prototype.slice.call(arguments).join(' ');
              postBridge({ type: 'log', message: '[Console.warn] ' + args });
            };

            var origErr = console.error;
            console.error = function() {
              origErr.apply(console, arguments);
              var args = Array.prototype.slice.call(arguments).join(' ');
              postBridge({ type: 'log', message: '[Console.error] ' + args });
            };

            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            var player = null;
            var isReady = false;
            var pendingId = null;
            var userWantsPlayback = false;
            var isAutoResuming = false;

            function onYouTubeIframeAPIReady() {
              postBridge({ type: 'log', message: 'onYouTubeIframeAPIReady invoked' });
              player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                host: 'https://www.youtube.com',
                playerVars: {
                  'autoplay': 1,
                  'playsinline': 1,
                  'controls': 0,
                  'disablekb': 1,
                  'fs': 0,
                  'rel': 0,
                  'modestbranding': 1,
                  'iv_load_policy': 3,
                  'enablejsapi': 1,
                  'origin': 'https://spasiba.vercel.app'
                },
                events: {
                  'onReady': onPlayerReady,
                  'onStateChange': onPlayerStateChange,
                  'onError': onPlayerError
                }
              });
            }

            function onPlayerReady(event) {
              isReady = true;
              postBridge({ type: 'log', message: 'onPlayerReady fired' });
              postBridge({ type: 'ready' });
              if (pendingId) {
                var target = pendingId;
                pendingId = null;
                loadVideo(target);
              }
            }

            function onPlayerStateChange(event) {
              postBridge({ type: 'log', message: 'onPlayerStateChange: ' + event.data });
              if (event.data === 1) { // PLAYING
                isAutoResuming = false;
                try {
                  if (typeof player.unMute === 'function') player.unMute();
                  if (typeof player.setVolume === 'function') player.setVolume(100);
                } catch (e) {}
              } else if (event.data === 2) { // PAUSED
                // If paused unexpectedly while user wants playback (e.g. system background transition)
                if (userWantsPlayback && !isAutoResuming) {
                  isAutoResuming = true;
                  setTimeout(function() {
                    if (userWantsPlayback && player && player.getPlayerState && player.getPlayerState() === 2) {
                      postBridge({ type: 'log', message: 'Re-asserting playback after unexpected pause' });
                      player.playVideo();
                    }
                    isAutoResuming = false;
                  }, 200);
                }
              }
              postBridge({
                type: 'stateChange',
                state: event.data,
                currentTime: (player && player.getCurrentTime) ? player.getCurrentTime() : 0,
                duration: (player && player.getDuration) ? player.getDuration() : 0
              });
            }

            function onPlayerError(event) {
              postBridge({ type: 'log', message: 'onPlayerError: ' + event.data });
              postBridge({
                type: 'error',
                code: event.data
              });
            }

            function loadVideo(videoId) {
              postBridge({ type: 'log', message: 'loadVideo called with: ' + videoId });
              userWantsPlayback = true;
              if (!isReady || !player) {
                postBridge({ type: 'log', message: 'Player not ready, pendingId set: ' + videoId });
                pendingId = videoId;
                return;
              }
              try {
                if (typeof player.unMute === 'function') {
                  player.unMute();
                }
                if (typeof player.setVolume === 'function') {
                  player.setVolume(100);
                }
                if (typeof player.loadVideoById === 'function') {
                  player.loadVideoById({ videoId: videoId, startSeconds: 0 });
                  player.playVideo();
                  postBridge({ type: 'log', message: 'player.loadVideoById executed for: ' + videoId });
                } else if (typeof player.cueVideoById === 'function') {
                  player.cueVideoById({ videoId: videoId });
                  player.playVideo();
                  postBridge({ type: 'log', message: 'player.cueVideoById + play executed for: ' + videoId });
                } else {
                  postBridge({ type: 'log', message: 'No loadVideoById method found on player' });
                }
              } catch (e) {
                postBridge({ type: 'log', message: 'loadVideo exception: ' + e.toString() });
              }
            }

            function playVideo() {
              userWantsPlayback = true;
              if (player && player.playVideo) {
                player.playVideo();
              }
            }

            function pauseVideo() {
              userWantsPlayback = false;
              if (player && player.pauseVideo) {
                player.pauseVideo();
              }
            }

            function seekToTime(seconds) {
              if (player && player.seekTo) {
                player.seekTo(seconds, true);
              }
            }

            function setPlaybackRate(rate) {
              if (player && player.setPlaybackRate) {
                player.setPlaybackRate(rate);
              }
            }

            // Continuous time ticker (every 250ms during playback)
            setInterval(function() {
              if (player && player.getPlayerState && player.getPlayerState() === 1) {
                postBridge({
                  type: 'timeUpdate',
                  currentTime: player.getCurrentTime(),
                  duration: player.getDuration()
                });
              }
            }, 250);
          </script>
        </body>
        </html>
        """

        let baseURL = URL(string: "https://spasiba.vercel.app")!
        webView?.loadHTMLString(html, baseURL: baseURL)
    }

    // MARK: - Public Playback Control API
    public func loadAndPlay(videoId: String) {
        self.userWantsPlayback = true
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            _ = self.getOrCreateWebView()
            if self.isReady {
                let js = "loadVideo('\(videoId)');"
                self.webView?.evaluateJavaScript(js) { _, error in
                    if let error = error {
                        NSLog("[HeadlessAudioBridge] evaluateJavaScript error: %@", error.localizedDescription)
                    } else {
                        NSLog("[HeadlessAudioBridge] evaluateJavaScript dispatched for: %@", videoId)
                    }
                }
            } else {
                self.pendingVideoId = videoId
                NSLog("[HeadlessAudioBridge] Queued pending videoId: %@", videoId)
            }
        }
    }

    public func play() {
        self.userWantsPlayback = true
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.webView?.evaluateJavaScript("playVideo();", completionHandler: nil)
        }
    }

    public func pause() {
        self.userWantsPlayback = false
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.webView?.pauseAllMediaPlayback()
            self.webView?.evaluateJavaScript("pauseVideo();", completionHandler: nil)
        }
    }

    public func seek(to seconds: Double) {
        DispatchQueue.main.async { [weak self] in
            self?.webView?.evaluateJavaScript("seekToTime(\(seconds));", completionHandler: nil)
        }
    }

    public func setPlaybackRate(_ rate: Float) {
        DispatchQueue.main.async { [weak self] in
            self?.webView?.evaluateJavaScript("setPlaybackRate(\(rate));", completionHandler: nil)
        }
    }

    // MARK: - WKScriptMessageHandler
    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let dict = message.body as? [String: Any],
              let type = dict["type"] as? String else { return }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            switch type {
            case "log":
                if let msg = dict["message"] as? String {
                    NSLog("[HeadlessAudioBridge JS] %@", msg)
                }

            case "ready":
                NSLog("[HeadlessAudioBridge] YouTube IFrame API Ready!")
                self.isReady = true
                if let pending = self.pendingVideoId {
                    self.pendingVideoId = nil
                    self.loadAndPlay(videoId: pending)
                }

            case "stateChange":
                if let state = dict["state"] as? Int {
                    // 1: Playing, 2: Paused, 3: Buffering, 0: Ended
                    switch state {
                    case 1: // Playing
                        self.isPlaying = true
                        self.isBuffering = false
                        self.onStateChange?(true, false)
                    case 2: // Paused
                        self.isPlaying = false
                        self.isBuffering = false
                        self.onStateChange?(false, false)
                    case 3: // Buffering
                        self.isBuffering = true
                        self.onStateChange?(self.isPlaying, true)
                    case 0: // Ended
                        self.isPlaying = false
                        self.isBuffering = false
                        self.userWantsPlayback = false
                        self.onStateChange?(false, false)
                        self.onTrackEnded?()
                    default:
                        break
                    }
                }
                if let cur = dict["currentTime"] as? Double, cur > 0 {
                    self.currentTime = cur
                }
                if let dur = dict["duration"] as? Double, dur > 0 {
                    self.duration = dur
                }

            case "timeUpdate":
                if let cur = dict["currentTime"] as? Double {
                    self.currentTime = cur
                }
                if let dur = dict["duration"] as? Double, dur > 0 {
                    self.duration = dur
                }
                self.onTimeUpdate?(self.currentTime, self.duration)

            case "error":
                let code = dict["code"] as? Int ?? -1
                NSLog("[HeadlessAudioBridge] Player Error Code: %d", code)
                self.onError?(code)

            default:
                break
            }
        }
    }

    // MARK: - WKNavigationDelegate
    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        NSLog("[HeadlessAudioBridge] Headless bridge webView finished loading base HTML")
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        NSLog("[HeadlessAudioBridge] Headless bridge webView failed navigation: %@", error.localizedDescription)
    }
}

public struct HeadlessAudioBridgeRepresentable: UIViewRepresentable {
    public init() {}

    public func makeUIView(context: Context) -> WKWebView {
        return HeadlessAudioBridge.shared.getOrCreateWebView()
    }

    public func updateUIView(_ uiView: WKWebView, context: Context) {
        // Maintained by HeadlessAudioBridge singleton
    }
}
