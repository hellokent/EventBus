EventBus
========

EventBus for Android

使用方法：
---------

  1. 确定Event：实现一个继承于BaseEvent的Event类
  
  2. 在需要EventBus处，使用EventBus.register(Object)注册自己，解注册时，调用unregister方法
  
  3. 发送事件时，调用Event.post()
  
  4. 注册Callback，在register的类里面写任意一个方法，方法的参数类型只有一个且继承BaseEvent。
     在方法命名处添加Event(runOn=MAIN)注解
     runOn可以是MAIN（主线程中调用），SOURCE（在来源处同步调用）和BACKGROUND（后台线程中调用） 
     
````
      public class PublishQuestionEvent extends BaseEvent<Question> {
        public PublishQuestionEvent(final Question data) {
            super(data);
        }
      }
      //注册处
      @Event(runOn = MAIN)
      void onPublishQuestion(final PublishQuestionEvent event) {
          mModuleList.add(0, event.getData());
          mAdapter.notifyDataSetChanged();
      }

      //调用处
      BUS.post(new PublishQuestionEvent(result));

````
  
最佳实践：
---------

  在Android开发中，在BaseActivity处声明一个static的EventBus，然后在OnCreate注册，OnDestory解注册
