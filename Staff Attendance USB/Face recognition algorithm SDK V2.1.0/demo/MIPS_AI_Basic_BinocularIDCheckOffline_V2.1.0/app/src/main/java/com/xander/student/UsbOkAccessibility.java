package com.xander.student;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Auto-clicks OK on the system USB permission dialog
 * ("Allow Staff Attendance to access IC Reader?").
 */
public class UsbOkAccessibility extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            String blob = collect(root) + " " + eventText(event);
            String low = blob.toLowerCase();
            boolean usbDlg = low.contains("ic reader")
                    || (low.contains("usb") && low.contains("staff attendance"));
            if (!usbDlg) {
                return;
            }
            if (!clickLabel(root, "OK")
                    && !clickLabel(root, "Allow")
                    && !clickLabel(root, "ALLOW")
                    && !clickLabel(root, "Ok")) {
                clickFirstButton(root);
            }
        } finally {
            try {
                root.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    private static String eventText(AccessibilityEvent event) {
        StringBuilder b = new StringBuilder();
        if (event.getText() != null) {
            for (CharSequence c : event.getText()) {
                if (c != null) {
                    b.append(c).append(' ');
                }
            }
        }
        if (event.getContentDescription() != null) {
            b.append(event.getContentDescription()).append(' ');
        }
        if (event.getClassName() != null) {
            b.append(event.getClassName());
        }
        return b.toString();
    }

    private static String collect(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        CharSequence t = node.getText();
        if (t != null) {
            b.append(t).append(' ');
        }
        CharSequence d = node.getContentDescription();
        if (d != null) {
            b.append(d).append(' ');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                b.append(collect(child));
                try {
                    child.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        return b.toString();
    }

    private static boolean clickLabel(AccessibilityNodeInfo root, String label) {
        java.util.List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(label);
        if (found == null) {
            return false;
        }
        boolean clicked = false;
        for (AccessibilityNodeInfo n : found) {
            if (n == null) {
                continue;
            }
            if (clickNode(n)) {
                clicked = true;
            }
            try {
                n.recycle();
            } catch (Exception ignored) {
            }
        }
        return clicked;
    }

    private static boolean clickFirstButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence cls = node.getClassName();
        if (node.isClickable() && cls != null && cls.toString().contains("Button")) {
            CharSequence t = node.getText();
            String s = t == null ? "" : t.toString().toLowerCase();
            if (!s.contains("cancel") && !s.contains("deny") && clickNode(node)) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (clickFirstButton(child)) {
                    try {
                        child.recycle();
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                try {
                    child.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private static boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        while (cur != null) {
            if (cur.isClickable()) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            cur = cur.getParent();
        }
        return false;
    }
}
