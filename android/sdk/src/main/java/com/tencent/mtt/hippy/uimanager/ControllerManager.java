/* Tencent is pleased to support the open source community by making Hippy available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
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
 */
package com.tencent.mtt.hippy.uimanager;

import android.annotation.SuppressLint;
import android.content.res.Resources.NotFoundException;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import com.tencent.mtt.hippy.HippyEngineContext;
import com.tencent.mtt.hippy.HippyInstanceLifecycleEventListener;
import com.tencent.mtt.hippy.HippyAPIProvider;
import com.tencent.mtt.hippy.HippyRootView;
import com.tencent.mtt.hippy.annotation.HippyController;
import com.tencent.mtt.hippy.common.HippyArray;
import com.tencent.mtt.hippy.common.HippyMap;
import com.tencent.mtt.hippy.common.HippyTag;
import com.tencent.mtt.hippy.dom.node.NodeProps;
import com.tencent.mtt.hippy.dom.node.StyleNode;
import com.tencent.mtt.hippy.modules.Promise;
import com.tencent.mtt.hippy.utils.ContextHolder;
import com.tencent.mtt.hippy.utils.DimensionsUtil;
import com.tencent.mtt.hippy.utils.LogUtils;
import com.tencent.mtt.hippy.utils.PixelUtil;
import com.tencent.mtt.hippy.utils.UIThreadUtils;
import com.tencent.mtt.hippy.views.custom.HippyCustomPropsController;
import com.tencent.mtt.hippy.views.list.HippyRecycler;
import com.tencent.mtt.hippy.views.scroll.HippyHorizontalScrollView;
import com.tencent.mtt.hippy.views.view.HippyViewGroupController;

import java.lang.reflect.Field;
import java.util.List;

@SuppressWarnings({"deprecation", "unchecked", "rawtypes", "unused"})
public class ControllerManager implements HippyInstanceLifecycleEventListener {

  final HippyEngineContext mContext;
  final ControllerRegistry mControllerRegistry;
  final ControllerUpdateManger<HippyViewController, View> mControllerUpdateManger;
  final SparseArray<View> mPreCacheView = new SparseArray<>();

  public ControllerManager(HippyEngineContext context, List<HippyAPIProvider> hippyPackages) {
    mContext = context;
    mControllerRegistry = new ControllerRegistry(context);
    mControllerUpdateManger = new ControllerUpdateManger();
    mContext.addInstanceLifecycleEventListener(this);
    processControllers(hippyPackages);
    mControllerUpdateManger.setCustomPropsController(mControllerRegistry.getViewController(
        HippyCustomPropsController.CLASS_NAME));
  }

  private void processControllers(List<HippyAPIProvider> hippyPackages) {
    addControllers(hippyPackages);
    mControllerRegistry.addControllerHolder(NodeProps.ROOT_NODE,
        new ControllerHolder(new HippyViewGroupController(), false));
  }

  /**
   * Add view controllers defined in {@link HippyAPIProvider}.
   *
   * @param hippyPackages API providers need to be added.
   */
  public void addControllers(List<HippyAPIProvider> hippyPackages) {
    if (hippyPackages == null) {
      return;
    }
    for (HippyAPIProvider hippyPackage : hippyPackages) {
      List<Class<? extends HippyViewController>> components = hippyPackage.getControllers();
      if (components != null) {
        for (Class hippyComponent : components) {
          HippyController hippyNativeModule = (HippyController) hippyComponent
            .getAnnotation(HippyController.class);
          assert hippyNativeModule != null;
          String name = hippyNativeModule.name();
          String[] names = hippyNativeModule.names();
          boolean lazy = hippyNativeModule.isLazyLoad();
          try {
            ControllerHolder holder = new ControllerHolder(
              (HippyViewController) hippyComponent.newInstance(), lazy);
            mControllerRegistry.addControllerHolder(name, holder);
            if (names.length > 0) {
              for (String s : names) {
                mControllerRegistry.addControllerHolder(s, holder);
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  public void destroy() {
    mContext.removeInstanceLifecycleEventListener(this);
    UIThreadUtils.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        int count = mControllerRegistry.getRootViewCount();
        if (count > 0) {
          for (int i = count - 1; i >= 0; i--) {
            deleteRootView(mControllerRegistry.getRootIDAt(i));
          }
        }
      }
    });
  }

  public View findView(int id) {
    return mControllerRegistry.getView(id);
  }

  public boolean hasView(int id) {
    return mControllerRegistry.getView(id) != null;
  }

  public void createPreView(HippyRootView rootView, int id, String className,
      HippyMap initialProps) {
    View view = mControllerRegistry.getView(id);
    if (view == null) {
      HippyViewController controller = mControllerRegistry.getViewController(className);
      view = controller.createView(rootView, id, mContext, className, initialProps);

      mPreCacheView.put(id, view);
    }

  }

  public View createView(HippyRootView rootView, int id, String className, HippyMap initialProps) {
    View view = mControllerRegistry.getView(id);
    if (view == null) {
      //first get the preView
      view = mPreCacheView.get(id);

      mPreCacheView.remove(id);

      HippyViewController controller = mControllerRegistry.getViewController(className);
      if (view == null) {
        view = controller.createView(rootView, id, mContext, className, initialProps);
      }

      if (view != null) {
        mControllerRegistry.addView(view);
        mControllerUpdateManger.updateProps(controller, view, initialProps);
        controller.onAfterUpdateProps(view);
      }
    }
    //		mContext.getGlobalConfigs().getLogAdapter().log(TAG, " createView id:" + id + " className:" + className + " view is null" + (view == null));
    return view;
  }

  public StyleNode createStyleNode(String className, boolean isVirtual, int rootId) {
    StyleNode tempNode = mControllerRegistry.getViewController(className)
        .createNode(isVirtual, rootId);
    if (tempNode != null) {
      return tempNode;
    }
    return mControllerRegistry.getViewController(className).createNode(isVirtual);
  }


  public void updateView(int id, String name, HippyMap newProps) {
    View view = mControllerRegistry.getView(id);
    HippyViewController viewComponent = mControllerRegistry.getViewController(name);
    if (view != null && viewComponent != null && newProps != null) {
      mControllerUpdateManger.updateProps(viewComponent, view, newProps);
      viewComponent.onAfterUpdateProps(view);
    }
  }


  public void updateLayout(String name, int id, int x, int y, int width, int height) {
    HippyViewController component = mControllerRegistry.getViewController(name);
    component.updateLayout(id, x, y, width, height, mControllerRegistry);
  }

  @Override
  public void onInstanceLoad(int instanceId) {
    if (mContext != null && mContext.getInstance(instanceId) != null) {
      mControllerRegistry.addRootView(mContext.getInstance(instanceId));
    }
  }

  @Override
  public void onInstanceResume(int instanceId) {

  }

  @Override
  public void onInstancePause(int instanceId) {

  }

  @Override
  public void onInstanceDestroy(int instanceId) {

  }

  public void updateExtra(int viewID, String name, Object object) {
    HippyViewController component = mControllerRegistry.getViewController(name);
    View view = mControllerRegistry.getView(viewID);
    component.updateExtra(view, object);
  }


  public void move(int id, int toId, int index) {
    View view = mControllerRegistry.getView(id);

    if (view != null) {
      if (view.getParent() != null) {
        ViewGroup oldParent = (ViewGroup) view.getParent();
        oldParent.removeView(view);
      }
      ViewGroup newParent = (ViewGroup) mControllerRegistry.getView(toId);
      if (newParent != null) {
        String parentClassName = HippyTag.getClassName(newParent);
        mControllerRegistry.getViewController(parentClassName).addView(newParent, view, index);
      }

      //			newParent.addView(view, index);
      LogUtils.d("ControllerManager", "move id: " + id + " toid: " + toId);
      //			mContext.getGlobalConfigs().getLogAdapter().log(TAG, "move id: " + id + " toid: " + toId);
    }


  }

  public boolean isControllerLazy(String className) {
    return mControllerRegistry.getControllerHolder(className).isLazy;
  }

  public void replaceID(int oldId, int newId) {
    View view = mControllerRegistry.getView(oldId);
    mControllerRegistry.removeView(oldId);

    if (view == null) {
      //			Toast.makeText(mControllerRegistry.getRootView(mControllerRegistry.getRootIDAt(0)).getContext(),"replaceID时候出异常了",Toast.LENGTH_LONG).show();
      //			Debug.waitForDebugger();
      LogUtils.d("HippyListView", "error replaceID null oldId " + oldId);
    } else {
      if (view instanceof HippyRecycler) {
        ((HippyRecycler) view).clear();
      }

      view.setId(newId);

      if (view instanceof HippyHorizontalScrollView) {
        ((HippyHorizontalScrollView)view).setContentOffset4Reuse();
      }

      mControllerRegistry.addView(view);
    }
  }

  public RenderNode createRenderNode(int id, HippyMap props, String className,
      HippyRootView hippyRootView, boolean lazy) {
    return mControllerRegistry.getViewController(className)
        .createRenderNode(id, props, className, hippyRootView, this, lazy);
  }

  public void dispatchUIFunction(int id, String className, String functionName, HippyArray var,
      Promise promise) {
    HippyViewController hippyViewController = mControllerRegistry.getViewController(className);
    View view = mControllerRegistry.getView(id);
    if (!promise.isCallback()) {
      hippyViewController.dispatchFunction(view, functionName, var);
    } else {
      hippyViewController.dispatchFunction(view, functionName, var, promise);
    }

  }

  public void onBatchComplete(String className, int id) {
    HippyViewController hippyViewController = mControllerRegistry.getViewController(className);
    View view = mControllerRegistry.getView(id);
    if (view != null) {
      hippyViewController.onBatchComplete(view);
    }
  }
	public void onBatchStart(String className, int id) {
		HippyViewController hippyViewController = mControllerRegistry.getViewController(className);
		View view = mControllerRegistry.getView(id);
		if (view != null) {
			hippyViewController.onBatchStart(view);
		}
	}
  public void deleteChildRecursive(ViewGroup viewParent, View child, int childIndex) {
    if (viewParent == null || child == null) {
      return;
    }
    HippyViewController hippyChildViewController = null;
    String childTagString = HippyTag.getClassName(child);
    if (!TextUtils.isEmpty(childTagString)) {
      hippyChildViewController = mControllerRegistry.getViewController(childTagString);
      if (hippyChildViewController != null) {
        hippyChildViewController.onViewDestroy(child);
      }
    }

    if (child instanceof ViewGroup) {
      ViewGroup childViewGroup = (ViewGroup) child;
      if (hippyChildViewController != null) {
        for (int i = hippyChildViewController.getChildCount(childViewGroup) - 1; i >= 0; i--) {
          deleteChildRecursive(childViewGroup,
              hippyChildViewController.getChildAt(childViewGroup, i), -1);
        }
      } else {
        for (int i = childViewGroup.getChildCount() - 1; i >= 0; i--) {
          deleteChildRecursive(childViewGroup, childViewGroup.getChildAt(i), -1);
        }
      }
    }

    if (mControllerRegistry.getView(child.getId()) != child
        && mControllerRegistry.getView(viewParent.getId()) != viewParent) {
      return;
    }

    String parentTagString = HippyTag.getClassName(viewParent);
    if (parentTagString != null) {
      //remove component Like listView there is a RecycleItemView is not js UI
      if (mControllerRegistry.getControllerHolder(parentTagString) != null) {
        HippyViewController hippyViewController = mControllerRegistry.getViewController(
            parentTagString);
        hippyViewController.deleteChild(viewParent, child, childIndex);
        //				LogUtils.d("HippyListView", "delete " + child.getId());
      }
    } else {
      viewParent.removeView(child);
    }

    //		mContext.getGlobalConfigs().getLogAdapter().log(TAG, "deleteChildRecursive id:" + child.getId() + " className:" + childTagString);
    mControllerRegistry.removeView(child.getId());
  }

  public void deleteChild(int pId, int childId) {
    deleteChild(pId, childId, -1);
  }

  public void deleteChild(int pId, int childId, int childIndex) {
    View parentView = mControllerRegistry.getView(pId);
    View childView = mControllerRegistry.getView(childId);
    if (parentView instanceof ViewGroup && childView != null) {
      deleteChildRecursive((ViewGroup) parentView, childView, childIndex);
    }
  }

  @SuppressLint("Range")
  public void measureInWindow(int id, Promise promise) {
    View v = mControllerRegistry.getView(id);
    if (v == null) {
      promise.reject("this view is null");
    } else {
      int[] outputBuffer = new int[4];
      int statusBarHeight;
      try {
        v.getLocationOnScreen(outputBuffer);

        // We need to remove the status bar from the height.  getLocationOnScreen will include the
        // status bar.
        statusBarHeight = DimensionsUtil.getStatusBarHeight();
        if (statusBarHeight > 0) {
          outputBuffer[1] -= statusBarHeight;
        }

        // outputBuffer[0,1] already contain what we want
        outputBuffer[2] = v.getWidth();
        outputBuffer[3] = v.getHeight();
      } catch (Throwable e) {
        promise.reject("exception" + e.getMessage());
        e.printStackTrace();
        return;
      }

      float x = PixelUtil.px2dp(outputBuffer[0]);
      float y = PixelUtil.px2dp(outputBuffer[1]);
      float width = PixelUtil.px2dp(outputBuffer[2]);
      float height = PixelUtil.px2dp(outputBuffer[3]);
      float fStatusbarHeight = PixelUtil.px2dp(statusBarHeight);

      HippyMap hippyMap = new HippyMap();
      hippyMap.pushDouble("x", x);
      hippyMap.pushDouble("y", y);
      hippyMap.pushDouble("width", width);
      hippyMap.pushDouble("height", height);
      hippyMap.pushDouble("statusBarHeight", fStatusbarHeight);
      promise.resolve(hippyMap);
    }

  }

  public void onManageChildComplete(String className, int id) {
    HippyViewController hippyViewController = mControllerRegistry.getViewController(className);
    View view = mControllerRegistry.getView(id);
    if (view != null) {
      hippyViewController.onManageChildComplete(view);
    }
  }

  public void addChild(int pid, int id, int index) {
    View childView = mControllerRegistry.getView(id);
    View parentView = mControllerRegistry.getView(pid);

    if (childView != null && parentView instanceof ViewGroup) {
      if (childView.getParent() == null) {
        LogUtils.d("ControllerManager", "addChild id: " + id + " pid: " + pid);
        //				mContext.getGlobalConfigs().getLogAdapter().log( TAG,"addChild id: " + id + " pid: " + pid);
        // childView.getParent()==null  this is the move action do first  so the child has a parent we do nothing  temp

        String parentClassName = HippyTag.getClassName(parentView);
        mControllerRegistry.getViewController(parentClassName)
            .addView((ViewGroup) parentView, childView, index);
      }
//			else
//			{
//				mContext.getGlobalConfigs().getLogAdapter().log( TAG,"addChild error childView has parent id: " + id + " pid: " + pid);
//			}
    } else {
      RenderNode parentNode = mContext.getRenderManager().getRenderNode(pid);
      String renderNodeClass = "null";
      if (parentNode != null) {
        renderNodeClass = parentNode.getClassName();
      }

      // 上报重要错误
      // 这个错误原因是：前端用了某个UI控件来做父亲，而这个UI控件实际上是不应该做父亲的（不是ViewGroup），务必要把这个parentView的className打出来
      String parentTag = null, parentClass = null, childTag = null, childClass = null;
      if (parentView != null) {
        Object temp = HippyTag.getClassName(parentView);
        if (temp != null) {
          parentTag = temp.toString();
        }
        parentClass = parentView.getClass().getName();
      }
      if (childView != null) {
        Object temp = HippyTag.getClassName(childView);
        if (temp != null) {
          childTag = temp.toString();
        }
        childClass = childView.getClass().getName();
      }
      Exception exception = new RuntimeException("child null or parent not ViewGroup pid " + pid
          + " parentTag " + parentTag
          + " parentClass " + parentClass
          + " renderNodeClass " + renderNodeClass + " id " + id
          + " childTag " + childTag
          + " childClass " + childClass);
      mContext.getGlobalConfigs().getExceptionHandler().handleNativeException(exception, true);
    }
  }

  public void deleteRootView(int mId) {

    View view = mControllerRegistry.getRootView(mId);
    if (view != null) {
      HippyRootView hippyRootView = (HippyRootView) view;
      int count = hippyRootView.getChildCount();

      for (int i = count - 1; i >= 0; i--) {
        deleteChild(mId, hippyRootView.getChildAt(i).getId());
      }
    }
    mControllerRegistry.removeRootView(mId);
  }
}
