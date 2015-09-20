/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This is a small port of the "San Angeles Observation" demo
 * program for OpenGL ES 1.x. For more details, see:
 *
 *    http://jet.ro/visuals/san-angeles-observation/
 *
 * This program demonstrates how to use a GLSurfaceView from Java
 * along with native OpenGL calls to perform frame rendering.
 *
 * Touching the screen will start/stop the animation.
 *
 * Note that the demo runs much faster on the emulator than on
 * real devices, this is mainly due to the following facts:
 *
 * - the demo sends bazillions of polygons to OpenGL without
 *   even trying to do culling. Most of them are clearly out
 *   of view.
 *
 * - on a real device, the GPU bus is the real bottleneck
 *   that prevent the demo from getting acceptable performance.
 *
 * - the software OpenGL engine used in the emulator uses
 *   the system bus instead, and its code rocks :-)
 *
 * Fixing the program to send less polygons to the GPU is left
 * as an exercise to the reader. As always, patches welcomed :-)
 */
package org.ab.uae;

import java.io.File;

import org.ab.controls.GameKeyListener;
import org.ab.controls.VirtualKeypad;

import retrobox.amiga.uae4droid.R;
import retrobox.vinput.GenericGamepad.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.QuitHandler;
import retrobox.vinput.QuitHandler.QuitHandlerCallback;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrobox.vinput.overlay.ExtraButtons;
import retrobox.vinput.overlay.ExtraButtonsController;
import retrobox.vinput.overlay.ExtraButtonsView;
import retrobox.vinput.overlay.GamepadController;
import retrobox.vinput.overlay.GamepadView;
import retrobox.vinput.overlay.Overlay;
import retrobox.vinput.overlay.OverlayExtra;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

class Globals {
	public static String ApplicationName = "uae";
}

public class DemoActivity extends Activity implements GameKeyListener {
	private static final int MOUSE_DOWN = 0;
	private static final int MOUSE_UP = 1;
	private static final int MOUSE_ABSOLUTE = 0;
	private static final int MOUSE_RELATIVE = 1;
	private static DemoActivity instance;
	private static String stateFileName;
	
	public static boolean aliased = true;
	
	public static Mapper mapper;
	public static VirtualInputDispatcher vinputDispatcher;
	
	static GamepadController gamepadController;
	static GamepadView gamepadView;
	static ExtraButtonsController extraButtonsController;
	static ExtraButtonsView extraButtonsView;
	
	public static final Overlay overlay = new Overlay();

	private static final String LOGTAG = DemoActivity.class.getSimpleName();
	protected VirtualKeypad vKeyPad = null;
	
	public class theKeyboardActionListener implements OnKeyboardActionListener{

        public void onKey(int primaryCode, int[] keyCodes) {
        	manageOnKey(primaryCode);
        }

        public void onPress(int primaryCode) {
        	if (mGLView != null) {
        		mGLView.actionKey(true, primaryCode);
        	}
        }

        public void onRelease(int primaryCode) {
        	if (mGLView != null) {
        		mGLView.actionKey(false, primaryCode);
        	}
        		
        }

        public void onText(CharSequence text) {}

     
        public void swipeDown() {}

     
        public void swipeLeft() {}

    
        public void swipeRight() {}

     
        public void swipeUp() {}};
        
        protected void manageOnKey(int c) {
    		if (c == Keyboard.KEYCODE_MODE_CHANGE) {
    			// switch layout
    			if (currentKeyboardLayout == 1)
    				switchKeyboard(2, false);
    			else if (currentKeyboardLayout == 2)
    				switchKeyboard(1, false);
    		} 
    	}
        
        protected void switchKeyboard(int newLayout, boolean preview) {
    		currentKeyboardLayout = newLayout;
    		if (theKeyboard != null) {
    			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    			boolean oldJoystick = sp.getBoolean("oldJoystick", false);
    			if (oldJoystick) {
    				theKeyboard.setKeyboard(layouts[currentKeyboardLayout]);
    				theKeyboard.setPreviewEnabled(preview);
    				if (mGLView != null) {
    					mGLView.shiftImage(touch&&joystick==1?SHIFT_KEYB:0);
    				}
    				vKeyPad = null;
    			} else {
	    			if (currentKeyboardLayout == 0) {
	    				 // on vire le keypad Android pour mettre celui de yongzh
						theKeyboard.setVisibility(View.INVISIBLE);
						vKeyPad = new VirtualKeypad(mGLView, this, R.drawable.dpad5, R.drawable.button);
						if (mGLView.getWidth() > 0)
							vKeyPad.resize(mGLView.getWidth(), mGLView.getHeight());
	    			} else {
	    				theKeyboard.setKeyboard(layouts[currentKeyboardLayout]);
	    				theKeyboard.setPreviewEnabled(preview);
	    				theKeyboard.setVisibility(touch?View.VISIBLE:View.INVISIBLE);
	    				vKeyPad = null;
	    			}
    			}
    		}
    	}
        
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        	if ("useInputMethod".equals(key))
        	getWindow().setFlags(prefs.getBoolean(key, false) ?
    				0 : WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
    				WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DemoActivity.instance = this;
        
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setImmersiveMode();
        
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = sp.edit();
        editor.putString("scale", getIntent().getBooleanExtra("keepAspect", true)?"scaled":"stretched");
        editor.commit();
        
        vinputDispatcher = new VirtualInputDispatcher();
        mapper = new Mapper(getIntent(), vinputDispatcher);
        Mapper.initGestureDetector(this);
        
		for(int i=0; i<2; i++) {
        	String prefix = "j" + (i+1);
        	String deviceDescriptor = getIntent().getStringExtra(prefix + "DESCRIPTOR");
        	Mapper.registerGamepad(i, deviceDescriptor);
        }
        
		gamepadController = new GamepadController();
		gamepadView = new GamepadView(this, overlay);
		
		extraButtonsController = new ExtraButtonsController();
		extraButtonsView = new ExtraButtonsView(this);

        
        stateFileName = getIntent().getStringExtra("state");
        aliased = getIntent().getBooleanExtra("linearFilter", true);
        
        /*TextView tv = new TextView(this);
        tv.setText("Initializing");
        setContentView(tv);
        downloader = new DataDownloader(this, tv);*/
        System.loadLibrary("uae2");
        checkConf();
        checkFiles(false);
        
     // touch controls by default if no physical keyboard
        if (getResources().getConfiguration().keyboard == Configuration.KEYBOARD_NOKEYS)
        	manageTouch(null);
    }
    
	public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) new Handler().postDelayed(new Runnable(){
			@Override
			public void run() {
				setImmersiveMode();
			}
		}, 5000);
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void setImmersiveMode() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(
		            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
		            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
		            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
		            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
		            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		} else {
			
		}
	}
    
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
    	if (gamepadView.isVisible() && gamepadController.onTouchEvent(ev)) {
    		if (Overlay.requiresRedraw) {
        		Overlay.requiresRedraw = false;
    			gamepadView.invalidate();
    		}
    		return true;
    	}
    	if (extraButtonsView.isVisible() && extraButtonsController.onTouchEvent(ev)) {
    		if (OverlayExtra.requiresRedraw) {
    			OverlayExtra.requiresRedraw = false;
    			extraButtonsView.invalidate();
    		}
    		return true;
    	}

    	mapper.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}



	protected static Thread nativeThread;
    public int joystick = 1;
    public boolean touch;
    public int mouse_button;
    
    private void checkConf() {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    	current_keycodes = new int [default_keycodes.length];
    	for(int i=0;i<default_keycodes.length;i++)
    		current_keycodes[i] = sp.getInt("key." + default_keycodes_string[i], default_keycodes[i]);
    }
    
    private void checkFiles(boolean force_reset) {
    	
    	File saveDir = new File(Environment.getExternalStorageDirectory().getPath() + "/.uae");
    	saveDir.mkdir();
    	
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    	onSharedPreferenceChanged(sp, "useInputMethod");
    	
        boolean twoPlayers = sp.getBoolean("twoPlayers", false);
        MainSurfaceView.setNumJoysticks(twoPlayers?2:1);
        
        String configFile = getIntent().getStringExtra("conf");
    	setPrefs(configFile);
    	setRightMouse(mouse_button);
    	initSDL();
    }
    
    public static int default_keycodes [] = {  KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_O,
    	KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
    	KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_V,
    	KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
    	KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
    	KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z,
    	KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
    	KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N};
    
    static {
        // to differentiate between joystick/special keys mapping and regular keyboard 
    	for(int i=0;i<default_keycodes.length;i++) default_keycodes[i] += 500;
    }
    public static String default_keycodes_string [] = { "Fire", "Alt.Fire" , "Left Mouse Click",
    	"Right Mouse Click", "Up", "Down", "Left",
    	"Right", "UpLeft", "UpRight", "DownLeft", "DownRight",
    	"Escape", "F1", "F2", "F3", "F4",
    	"F5", "F6", "F7", "F8", "Space",
    	"LeftShift", "RightShift", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
    	"Fire Joy2", "Up Joy2", "Down Joy2", "Left Joy2",
    	"Right Joy2", "UpLeft Joy2", "UpRight Joy2", "DownLeft Joy2", "DownRight Joy2"};
    public static int current_keycodes [];
    
    public int [] getRealKeyCode(int keyCode) {
    	int h [] = new int [2];
    	h[0] = keyCode;
    	h[1] = 1;
		for(int i=0;i<current_keycodes.length;i++) {
			if (keyCode == current_keycodes[i]) {
				if (default_keycodes[i] == default_keycodes[1]) {
					h[0] = default_keycodes[0];
					return h;
				}
				if (i > 27) {
					// joystick 2
					if (i == 28)
						h[0] = default_keycodes[0];
					else
						h[0] = default_keycodes[i-25];
					h[1] = 2;
					return h;
				} else {
					h[0] = default_keycodes[i];
					return h;
				}
			}
		}
    	return h;
    }

    
    protected KeyboardView theKeyboard;
	public KeyboardView getTheKeyboard() {
		return theKeyboard;
	}
	protected static int currentKeyboardLayout;
	protected Keyboard layouts [];
	
    public void initSDL()
    {
    	if (mGLView == null)
    	setContentView(R.layout.main);
    	mGLView = ((MainSurfaceView) findViewById(R.id.mainview));
    	
    	 // Receive keyboard events
        mGLView.setFocusableInTouchMode(true);
        mGLView.setFocusable(true);
        mGLView.requestFocus();
        setupGamepadOverlay();
/*
        if (!getIntent().hasExtra("gamepad")) {
	        vKeyPad = new VirtualKeypad(mGLView, this, R.drawable.dpad5, R.drawable.button);
			if (mGLView.getWidth() > 0)
				vKeyPad.resize(mGLView.getWidth(), mGLView.getHeight());
			
	        if (theKeyboard == null) {
		        theKeyboard = (KeyboardView) findViewById(R.id.EditKeyboard01);
		        layouts = new Keyboard [3];
		        layouts[0] = new Keyboard(this, R.xml.joystick);
		        layouts[1] = new Keyboard(this, R.xml.qwerty);
		        layouts[2] = new Keyboard(this, R.xml.qwerty2);
		        theKeyboard.setKeyboard(layouts[currentKeyboardLayout]);
		        theKeyboard.setOnKeyboardActionListener(new theKeyboardActionListener());
		        theKeyboard.setVisibility(View.INVISIBLE);
		        theKeyboard.setPreviewEnabled(false);
	        }
        }
*/
    }
    
	private boolean needsOverlay() {
		return getIntent().hasExtra("OVERLAY");
	}
	
	private void setupGamepadOverlay() {
		ViewTreeObserver observer = mGLView.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
		    	int w = mGLView.getWidth();
		    	int h = mGLView.getHeight();
				if (needsOverlay()) {
			    	String overlayConfig = getIntent().getStringExtra("OVERLAY");
					float alpha = getIntent().getFloatExtra("OVERLAY_ALPHA", 0.8f);
			    	if (overlayConfig!=null) overlay.init(overlayConfig, w, h, alpha);
				}
		
		    	Log.d("REMAP", "addExtraButtons : " + getIntent().getStringExtra("buttons"));
		        ExtraButtons.initExtraButtons(DemoActivity.this, getIntent().getStringExtra("buttons"), w, h, true);
			}
		});

		ViewGroup root = (ViewGroup)findViewById(R.id.root);
		
		if (needsOverlay()) {
			 gamepadView.addToLayout(root);
			 gamepadView.showPanel();
		}
		 
		extraButtonsView.addToLayout(root);
		extraButtonsView.hidePanel();
	}
    
    public void render() {
    	if (mGLView != null)
    		mGLView.requestRender();
    }

    @Override
    protected void onPause() {
        // TODO: if application pauses it's screen is messed up
       /* if( wakeLock != null )
            wakeLock.release();*/
        super.onPause();
        if( mGLView != null )
            mGLView.onPause();
    }

    @Override
    protected void onResume() {
      /*  if( wakeLock != null )
            wakeLock.acquire();*/
        super.onResume();
        if( mGLView != null )
            mGLView.onResume();
    }

    @Override
    protected void onDestroy() 
    {
      /*  if( wakeLock != null )
            wakeLock.release();*/
       
        if( mGLView != null )
            mGLView.exitApp();
        super.onStop();
        finish();
    }

    private static MainSurfaceView mGLView = null;
   
    
    static final private int CONFIGURE_ID = Menu.FIRST +1;
    static final private int INPUT_ID = Menu.FIRST +2;
    static final private int RESET_ID = Menu.FIRST +3;
    static final private int TOUCH_ID = Menu.FIRST +4;
    static final private int LOAD_ID = Menu.FIRST +5;
    static final private int SAVE_ID = Menu.FIRST +6;
    static final private int MOUSE_ID = Menu.FIRST +7;
    static final private int QUIT_ID = Menu.FIRST +8;
    static final private int SWAP_ID = Menu.FIRST +9;
    static final private int CANCEL_ID = Menu.FIRST +10;
    static final private int BUTTONS_ID = Menu.FIRST +11;
    static final private int OVERLAY_ID = Menu.FIRST +12;
    
    private AudioTrack audio;
    private boolean play;
    
    public void initAudio(int freq, int bits) {
    	if (audio == null) {
    		audio = new AudioTrack(AudioManager.STREAM_MUSIC, freq, AudioFormat.CHANNEL_CONFIGURATION_STEREO, bits == 8?AudioFormat.ENCODING_PCM_8BIT:AudioFormat.ENCODING_PCM_16BIT, freq==44100?32*1024:16*1024, AudioTrack.MODE_STREAM);
    		Log.i("UAE", "AudioTrack initialized: " + freq);
    		audio.play();
    	}
    }
    
    public int sendAudio(short data [], int size) {
    	if (audio != null) {
    		if (!play) {
    			play = true;
    		}
    		return audio.write(data, 0, size);
    	}
    	return -1;
    }
    
    public void pauseAudio() {
    	if (audio != null && play) {
    		audio.pause();
    		Log.i("UAE", "audio paused");
    	}
    	
    }
    
    public void playAudio() {
    	if (audio != null && play) {
    		audio.play();
    	}
    }
    
    public void stopAudio() {
    	if (audio != null && play) {
    		audio.stop();
    	}
    }
    
    private static final int SHIFT_KEYB = 150;
    
    public native void setPrefs(String configfile);
    public native void saveState(String filename, int num);
    public native void loadState(String filename, int num);
    public native String diskSwap();
    public native void nativeReset();
    public native void nativeQuit();
    public native void setRightMouse(int right);
    //public native void nativeAudioInit(DemoActivity callback);
    
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, CANCEL_ID, 0, "Cancel");
        menu.add(0, LOAD_ID, 0, R.string.load_state);
        menu.add(0, SAVE_ID, 0, R.string.save_state);
        if (OverlayExtra.hasExtraButtons()) {
        	menu.add(0, BUTTONS_ID, 0, "Extra Buttons");
        }
        if (needsOverlay()) {
        	menu.add(0, OVERLAY_ID, 0, "Overlay ON/OFF");
        }
        menu.add(0, SWAP_ID, 0, R.string.swap);
        menu.add(0, QUIT_ID, 0, R.string.quit);
        
        return true;
    }
    
    @Override
	public void onBackPressed() {
		openOptionsMenu();
	}

	public void uiLoadState() {
		loadState(stateFileName, 0);
		toastMessage("State was restored");
	}
    
    public void uiSaveState() {
		saveState(stateFileName, 0);
		toastMessage("State was saved");
    }
    
    public void uiSwapDisks() {
		String disk = DemoActivity.instance.diskSwap();
		toastMessage("Disk inserted on fd0: " + disk);
    }
    
    public void uiQuit() {
		nativeQuit();
    }
    
    protected void uiQuitConfirm() {
    	QuitHandler.askForQuit(this, new QuitHandlerCallback() {
			@Override
			public void onQuit() {
				uiQuit();
			}
		});
    }

    private void uiToggleExtraButtons() {
    	extraButtonsView.toggleView();
	}
    
    private void uiToggleOverlay() {
    	gamepadView.toggleView();
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	if (item != null) {
	        switch (item.getItemId()) {
	        case LOAD_ID : uiLoadState(); return true;
	        case SAVE_ID : uiSaveState(); return true;
	        case BUTTONS_ID : uiToggleExtraButtons(); return true;
	        case OVERLAY_ID : uiToggleOverlay(); return true;
	        case SWAP_ID : uiSwapDisks(); return true;
	        case QUIT_ID : uiQuit(); return true;
	        }
    	}
        return super.onMenuItemSelected(featureId, item);
    }
    
    private void manageTouch(MenuItem item) {
    	if (touch) {
			touch = false;
			if (item != null)
				item.setTitle(R.string.show_touch);
			if (theKeyboard != null)
				theKeyboard.setVisibility(View.INVISIBLE);
		} else {
			touch = true;
			if (item != null)
				item.setTitle(R.string.hide_touch);
			if (theKeyboard != null && currentKeyboardLayout != 0)
				theKeyboard.setVisibility(View.VISIBLE);
		}
		if (mGLView != null && vKeyPad == null) {
			mGLView.shiftImage(joystick==1&&touch?SHIFT_KEYB:0);
		}
    }
    
    @Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		onPause();
		return super.onMenuOpened(featureId, menu);
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		onResume();
		super.onOptionsMenuClosed(menu);
	}
    
private int currentKeyStates = 0;


public void onGameKeyChanged(int keyStates) {
	if (mGLView != null) {
		manageKey(keyStates, VirtualKeypad.BUTTON, current_keycodes[0]);
		manageKey(keyStates, VirtualKeypad.UP, current_keycodes[4]);
		manageKey(keyStates, VirtualKeypad.DOWN, current_keycodes[5]);
		manageKey(keyStates, VirtualKeypad.LEFT, current_keycodes[6]);
		manageKey(keyStates, VirtualKeypad.RIGHT, current_keycodes[7]);
		manageKey(keyStates, VirtualKeypad.UP | VirtualKeypad.LEFT, current_keycodes[8]);
		manageKey(keyStates, VirtualKeypad.UP | VirtualKeypad.RIGHT, current_keycodes[9]);
		manageKey(keyStates, VirtualKeypad.DOWN | VirtualKeypad.LEFT, current_keycodes[10]);
		manageKey(keyStates, VirtualKeypad.DOWN | VirtualKeypad.RIGHT, current_keycodes[11]);
	}
	
	currentKeyStates = keyStates;
	
}

private void manageKey(int keyStates, int key, int press) {
	 if ((keyStates & key) == key && (currentKeyStates & key) == 0) {
		// Log.i("FC64", "down: " + press );
		 mGLView.keyDown(press);
	 } else if ((keyStates & key) == 0 && (currentKeyStates & key) == key) {
		// Log.i("FC64", "up: " + press );
		 mGLView.keyUp(press);
	 }
}


private void toastMessage(final String message) {
	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
}

@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
	if (mGLView != null) {
		if (mapper.handleKeyEvent(event, keyCode, true)) return true;
		if (mGLView.keyDown(keyCode)) return true;
	}
	return super.onKeyDown(keyCode, event);
}

@Override
public boolean onKeyUp(int keyCode, KeyEvent event) {
	if (mGLView != null) {
		if (mapper.handleKeyEvent(event, keyCode, false)) return true;
		if (mGLView.keyUp(keyCode)) return true;
	}
	return super.onKeyUp(keyCode, event);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	
	 if (resultCode == RESULT_OK) {
		 if (requestCode == CONFIGURE_ID) {
			 onPause();
			 showDialog(1); 
		 }
	 }
}

@Override
protected Dialog onCreateDialog(int id) {
	
		switch (id) {
	   case 1: return new AlertDialog.Builder(DemoActivity.this)
       .setTitle(R.string.reset)
       .setMessage(R.string.reset_ask)
       .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int whichButton) {
        	   checkConf();
        	   checkFiles(true);
        	   onResume();
           }
       })
       .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int whichButton) {
        	  
        	   checkConf();
        	   checkFiles(false);
        	  onResume();
           }
       })
       .create();
	   case 2: return new AlertDialog.Builder(DemoActivity.this)
       .setTitle(R.string.quit)
       .setMessage(R.string.quit_info)
       .setPositiveButton(R.string.quit, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int whichButton) {
        	   nativeQuit();
           }
       })
       .create();
	   case 3: return new AlertDialog.Builder(DemoActivity.this)
       .setTitle(R.string.notes)
       .setMessage(R.string.release_notes_097)
       .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int whichButton) {
        	   onResume();
           }
       })
       
       .create();
		}
		return null;
}


class VirtualInputDispatcher implements VirtualEventDispatcher {

	@Override
	public void sendMouseButton(MouseButton button, boolean down) {
		Log.d("MAPPER", "Send native mouse button " + button + ", down:" + down);
		MainSurfaceView.nativeMouse(0, 0, down? MOUSE_DOWN: MOUSE_UP, button.ordinal(), MOUSE_RELATIVE);
	}
	
	@Override
	public void sendKey(int keyCode, boolean down) {
		int joystick = 0;
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BUTTON_1: keyCode = current_keycodes[0]; joystick = 1; break;
		case KeyEvent.KEYCODE_DPAD_UP: keyCode = current_keycodes[4]; joystick = 1; break;
		case KeyEvent.KEYCODE_DPAD_DOWN: keyCode = current_keycodes[5]; joystick = 1; break;
		case KeyEvent.KEYCODE_DPAD_LEFT: keyCode = current_keycodes[6]; joystick = 1; break;
		case KeyEvent.KEYCODE_DPAD_RIGHT: keyCode = current_keycodes[7]; joystick = 1; break;
		}
		
		Log.d("MAPPER", "Send native key " + keyCode + ", down:" + down);
		int pressed = down?1:0;
		if (joystick == 0) {
			MainSurfaceView.nativeKey(keyCode, pressed, 0, 0);
		} else {
			MainSurfaceView.nativeKey(keyCode, pressed, DemoActivity.instance.joystick, joystick);
		}
	}
	
	@Override
	public boolean handleShortcut(ShortCut shortcut, boolean down) {
		switch(shortcut) {
		case LOAD_STATE : if (!down) uiLoadState(); return true;
		case SAVE_STATE : if (!down) uiSaveState(); return true;
		case SWAP_DISK  : if (!down) uiSwapDisks(); return true;
		case MENU       : if (!down) openOptionsMenu(); return true;
		case EXIT       : uiQuitConfirm();return true;
		default: return false;
		}
	}

	@Override
	public void sendAnalog(Analog index, double x, double y) {}

}

}
