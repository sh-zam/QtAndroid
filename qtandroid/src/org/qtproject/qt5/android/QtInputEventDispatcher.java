package org.qtproject.qt5.android;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

public class QtInputEventDispatcher extends Thread {

    private static final String TAG = "QtInputEventDispatcher";

    private Handler mEventHandler;

    QtInputEventDispatcher() {
        super(TAG);
    }

    @Override
    public void run() {
        try {
            Looper.prepare();
            mEventHandler =
                    new Handler(
                            Looper.myLooper(),
                            new Handler.Callback() {
                                @Override
                                public boolean handleMessage(Message msg) {
                                    MotionEvent event = (MotionEvent) msg.obj;
                                    if (event != null) {
                                        dispatchMotionEvent(msg.arg1, msg.arg2 == 1, event);
                                        event.recycle();
                                    }
                                    return true;
                                }
                            });
            Looper.loop();
        } catch (Exception e) {
            Log.e(TAG, "Looper halted, error = " + e);
            e.printStackTrace();
        }
    }

    public void onCommonEvent(MotionEvent event, int id) {
        if (mEventHandler == null) {
            return;
        }

        MotionEvent clonedEvent = MotionEvent.obtain(event);
        Message.obtain(mEventHandler, /*what = */ 0, id, /* arg2 = */ 0, clonedEvent)
                .sendToTarget();
    }

    public void onTouchEvent(MotionEvent event, int id) {
        if (mEventHandler == null) {
            return;
        }

        MotionEvent clonedEvent = MotionEvent.obtain(event);
        // here we set touch to true
        Message.obtain(mEventHandler, /*what = */ 0, id, /* arg2 = */ 1, clonedEvent)
                .sendToTarget();
    }

    public void onLongPress(MotionEvent event, int id) {
        if (mEventHandler == null) {
            return;
        }

        Message.obtain(
                        mEventHandler,
                        new Runnable() {
                            @Override
                            public void run() {
                                longPress(id, (int) event.getX(), (int) event.getY());
                            }
                        })
                .sendToTarget();
    }

    public boolean sendGenericMotionEvent(MotionEvent event, int id) {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            return false;
        }

        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            onCommonEvent(event, id);
            return canHandleMouseAction(event);
        } else if ((event.getSource()
                        & (InputDevice.SOURCE_STYLUS
                                | InputDevice.SOURCE_TOUCHPAD
                                | InputDevice.SOURCE_TOUCHSCREEN))
                != 0) {

            onCommonEvent(event, id);
            return true;
        }
        return false;
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

    private static native void dispatchMotionEvent(
            int winId, boolean isTouchEvent, MotionEvent event);

    private static native void longPress(int winId, int x, int y);
}
