package org.qtproject.qt5.android;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

public class QtInputEventDispatcher extends Thread {

    private static final String TAG = "QtInputEventDispatcher";

    private static int sOldX, sOldY;
    private static Boolean sTabletEventSupported = null;

    private Handler mEventHandler;

    QtInputEventDispatcher() {
        super(TAG);
    }

    @Override
    public void run() {
        try {
            Looper.prepare();
            mEventHandler = new Handler(Looper.myLooper(), new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    MotionEvent event = (MotionEvent)msg.obj;
                    float tiltRot = event.getAxisValue(MotionEvent.AXIS_TILT);
                    float orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION);
                    float tiltX = (float) Math.toDegrees(-Math.sin(orientation) * tiltRot);
                    float tiltY = (float) Math.toDegrees(Math.cos(orientation) * tiltRot);
                    tabletEvent(
                            -1,
                            event.getDeviceId(),
                            System.currentTimeMillis(),
                            event.getActionMasked(),
                            1,
                            event.getButtonState(),
                            event.getX(),
                            event.getY(),
                            event.getPressure(),
                            tiltX,
                            tiltY,
                            (float) Math.toDegrees(orientation),
                            event.getMetaState());
                    event.recycle();
                    return true;
                }
            });
            Looper.loop();
        } catch (Exception e) {
            Log.e(TAG, "Looper halted, error = " + e);
            e.printStackTrace();
        }
    }

    public void onTouchEvent(MotionEvent event, int id) {
        sendTouchEvent(event, id);
    }

    public void onTrackballEvent(MotionEvent event, int id) {
        sendTrackballEvent(event, id);
    }

    public void onMouseEvent(MotionEvent event, int id) {
        sendMouseEvent(event, id);
    }

    public void onLongPress(MotionEvent event, int id) {
        dispatchLongPress(id, (int) event.getX(), (int) event.getY());
    }

    public boolean sendGenericMotionEvent(MotionEvent event, int id) {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            return false;
        }

        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            onMouseEvent(event, id);
            return canHandleMouseAction(event);
        } else if ((event.getSource()
                        & (InputDevice.SOURCE_STYLUS
                                | InputDevice.SOURCE_TOUCHPAD
                                | InputDevice.SOURCE_TOUCHSCREEN))
                != 0) {

            onTouchEvent(event, id);
            return true;
        }
        return false;
    }

    private static int getAction(int index, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            int hsz = event.getHistorySize();
            if (hsz > 0) {
                float x = event.getX(index);
                float y = event.getY(index);
                for (int h = 0; h < hsz; ++h) {
                    if (event.getHistoricalX(index, h) != x || event.getHistoricalY(index, h) != y)
                        return 1;
                }
                return 2;
            }
            return 1;
        }
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN && index == event.getActionIndex()) {
            return 0;
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP && index == event.getActionIndex()) {
            return 3;
        }
        return 2;
    }

    private boolean sendTouchEvent(MotionEvent event, int id) {
        int pointerType = 0;
        // Log.w(TAG, event.toString());
        // Log.w(TAG, "Time: " + event.getEventTime() + "  tp: " + event.getX() + ", " +
        // event.getY());

        if (sTabletEventSupported == null) sTabletEventSupported = isTabletEventSupported();

        switch (event.getToolType(0)) {
            case MotionEvent.TOOL_TYPE_STYLUS:
                pointerType = 1; // QTabletEvent::Pen
                break;
            case MotionEvent.TOOL_TYPE_ERASER:
                pointerType = 3; // QTabletEvent::Eraser
                break;
        }

        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            return sendMouseEvent(event, id);
        } else if (sTabletEventSupported && pointerType != 0) {
            final int historySize = event.getHistorySize();
            for (int h = 0; h < historySize; h++) {
                // Log.w(TAG, "h = " + historySize + ", " + event.getHistorySize());
                float tiltRot = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h);
                float orientation = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, h);

                float tiltX = (float) Math.toDegrees(-Math.sin(orientation) * tiltRot);
                float tiltY = (float) Math.toDegrees(Math.cos(orientation) * tiltRot);

                // dispatchTabletEvent(
                //         id,
                //         event.getDeviceId(),
                //         System.currentTimeMillis(),
                //         event.getActionMasked(),
                //         pointerType,
                //         event.getButtonState(),
                //         event.getHistoricalX(h),
                //         event.getHistoricalY(h),
                //         event.getHistoricalPressure(h),
                //         tiltX,
                //         tiltY,
                //         (float) Math.toDegrees(orientation),
                //         event.getMetaState());
            }
            float tiltRot = event.getAxisValue(MotionEvent.AXIS_TILT);
            float orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION);
            float tiltX = (float) Math.toDegrees(-Math.sin(orientation) * tiltRot);
            float tiltY = (float) Math.toDegrees(Math.cos(orientation) * tiltRot);
//             tabletEvent(
//                     id,
//                     event.getDeviceId(),
//                     System.currentTimeMillis(),
//                     event.getActionMasked(),
//                     pointerType,
//                     event.getButtonState(),
//                     event.getX(),
//                     event.getY(),
//                     event.getPressure(),
//                     tiltX,
//                     tiltY,
//                     (float) Math.toDegrees(orientation),
//                     event.getMetaState());

            Message.obtain(mEventHandler, 0,
            MotionEvent.obtain(event.getDownTime(), System.currentTimeMillis(),
                    event.getActionMasked(), event.getX(), event.getY(), event.getPressure(),
                    event.getSize(), event.getMetaState(), event.getXPrecision(), event.getYPrecision(),
                    event.getDeviceId(), event.getEdgeFlags())).sendToTarget();
            return true;
        } else {
            dispatchTouchBegin(id);
            for (int i = 0; i < event.getPointerCount(); ++i) {
                dispatchTouchAdd(
                        id,
                        event.getPointerId(i),
                        getAction(i, event),
                        i == 0,
                        (int) event.getX(i),
                        (int) event.getY(i),
                        event.getTouchMajor(i),
                        event.getTouchMinor(i),
                        event.getOrientation(i),
                        event.getPressure(i));
            }
            dispatchTouchEnd(id, event.getAction());
            return true;
        }
    }

    private void sendTrackballEvent(MotionEvent event, int id) {
        sendMouseEvent(event, id);
    }

    private static boolean canHandleMouseAction(MotionEvent event) {
        // TODO(sh-zam): use masks
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_SCROLL:
                return true;
            default:
                return false;
        }
    }

    private boolean sendMouseEvent(MotionEvent event, int id) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                dispatchMouseUp(id, (int) event.getX(), (int) event.getY(), event.getMetaState());
                break;

            case MotionEvent.ACTION_DOWN:
                dispatchMouseDown(
                        id,
                        (int) event.getX(),
                        (int) event.getY(),
                        event.getMetaState(),
                        event.getButtonState());
                sOldX = (int) event.getX();
                sOldY = (int) event.getY();
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                    dispatchMouseMove(
                            id, (int) event.getX(), (int) event.getY(), event.getMetaState());
                } else {
                    int dx = (int) (event.getX() - sOldX);
                    int dy = (int) (event.getY() - sOldY);
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        dispatchMouseMove(
                                id, (int) event.getX(), (int) event.getY(), event.getMetaState());
                        sOldX = (int) event.getX();
                        sOldY = (int) event.getY();
                    }
                }
                break;
            case MotionEvent.ACTION_SCROLL:
                dispatchMouseWheel(
                        id,
                        (int) event.getX(),
                        (int) event.getY(),
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                break;
            default:
                return false;
        }
        return true;
    }

    private void dispatchMouseDown(int winId, int x, int y, int modifier, int actionButton) {}

    private void dispatchMouseUp(int winId, int x, int y, int modifiers) {}

    private void dispatchMouseMove(int winId, int x, int y, int modifier) {}

    private void dispatchMouseWheel(int winId, int x, int y, float hdelta, float vdelta) {}

    private void dispatchTouchBegin(int winId) {
        Message.obtain(
                        mEventHandler,
                        new Runnable() {
                            @Override
                            public void run() {
                                touchBegin(winId);
                            }
                        })
                .sendToTarget();
    }

    private void dispatchTouchAdd(
            int winId,
            int pointerId,
            int action,
            boolean primary,
            int x,
            int y,
            float major,
            float minor,
            float rotation,
            float pressure) {
        Message.obtain(
                        mEventHandler,
                        new Runnable() {
                            @Override
                            public void run() {
                                touchAdd(
                                        winId, pointerId, action, primary, x, y, major, minor,
                                        rotation, pressure);
                            }
                        })
                .sendToTarget();
    }

    private void dispatchTouchEnd(int winId, int action) {
        Message.obtain(
                        mEventHandler,
                        new Runnable() {
                            @Override
                            public void run() {
                                touchEnd(winId, action);
                            }
                        })
                .sendToTarget();
    }

    private void dispatchLongPress(int winId, int x, int y) {
        if (mEventHandler == null) {
            return;
        }
        Message.obtain(
                        mEventHandler,
                        new Runnable() {
                            @Override
                            public void run() {
                                longPress(winId, x, y);
                            }
                        })
                .sendToTarget();
    }

    private void dispatchTabletEvent(
            int winId,
            int deviceId,
            long time,
            int action,
            int pointerType,
            int buttonState,
            float x,
            float y,
            float pressure,
            float tiltX,
            float tiltY,
            float rotation,
            int modifiers) {

        if (mEventHandler == null) {
            return;
        }
        Message msg =
                Message.obtain(
                        mEventHandler,
                        () ->
                                tabletEvent(
                                        winId,
                                        deviceId,
                                        time,
                                        action,
                                        pointerType,
                                        buttonState,
                                        x,
                                        y,
                                        pressure,
                                        tiltX,
                                        tiltY,
                                        rotation,
                                        modifiers));
        msg.sendToTarget();
    }

    // natives
    private static native void mouseDown(int winId, int x, int y, int modifier, int actionButton);

    private static native void mouseUp(int winId, int x, int y, int modifiers);

    private static native void mouseMove(int winId, int x, int y, int modifier);

    private static native void mouseWheel(int winId, int x, int y, float hdelta, float vdelta);

    private static native void touchBegin(int winId);

    private static native void touchAdd(
            int winId,
            int pointerId,
            int action,
            boolean primary,
            int x,
            int y,
            float major,
            float minor,
            float rotation,
            float pressure);

    private static native void touchEnd(int winId, int action);

    private static native void longPress(int winId, int x, int y);

    private static native boolean isTabletEventSupported();

    private static native void tabletEvent(
            int winId,
            int deviceId,
            long time,
            int action,
            int pointerType,
            int buttonState,
            float x,
            float y,
            float pressure,
            float tiltX,
            float tiltY,
            float rotation,
            int modifiers);

    public static native void keyDown(int key, int unicode, int modifier, boolean autoRepeat);

    public static native void keyUp(int key, int unicode, int modifier, boolean autoRepeat);

    public static native void keyboardVisibilityChanged(boolean visibility);

    public static native void keyboardGeometryChanged(int x, int y, int width, int height);

    public static native void handleLocationChanged(int id, int x, int y);
}
