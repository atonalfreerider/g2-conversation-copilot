// Hardware boundary for @evenrealities/even_hub_sdk. Kept uncompiled so the
// dependency-free prototype remains runnable before a real device is available.
export interface GlassesPort {
  show(lines: string[]): Promise<void>;
  onGesture(handler: (gesture: "press" | "double_press" | "swipe_up" | "swipe_down") => void): () => void;
  onPcm16k(handler: (pcm: Int16Array) => void): () => void;
}

// Production implementation:
// 1. waitForEvenAppBridge()
// 2. createStartUpPageContainer() with one event-capturing text container
// 3. textContainerUpgrade() for low-flicker card updates
// 4. onEvenHubEvent() to map R1/temple events and audio buffers
// 5. double press exits/stops capture; contextual-menu actions switch language/mode
