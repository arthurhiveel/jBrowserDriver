/*
 * jBrowserDriver (TM)
 * Copyright (C) 2014-2015 Machine Publishers, LLC
 * ops@machinepublishers.com | machinepublishers.com
 * Cincinnati, Ohio, USA
 *
 * You can redistribute this program and/or modify it under the terms of the
 * GNU Affero General Public License version 3 as published by the Free
 * Software Foundation. Additional permissions or commercial licensing may be
 * available--see LICENSE file or contact Machine Publishers, LLC for details.
 *
 * For general details about how to investigate and report license violations,
 * please see: https://www.gnu.org/licenses/gpl-violation.html
 * and email the author: ops@machinepublishers.com
 * Keep in mind that paying customers have more rights than the AGPL alone offers.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License version 3
 * for more details.
 */
package com.machinepublishers.jbrowserdriver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import netscape.javascript.JSObject;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.internal.FindsByClassName;
import org.openqa.selenium.internal.FindsByCssSelector;
import org.openqa.selenium.internal.FindsById;
import org.openqa.selenium.internal.FindsByLinkText;
import org.openqa.selenium.internal.FindsByName;
import org.openqa.selenium.internal.FindsByTagName;
import org.openqa.selenium.internal.FindsByXPath;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLFormElement;
import org.w3c.dom.html.HTMLInputElement;

import com.machinepublishers.jbrowserdriver.Robot.MouseButton;
import com.machinepublishers.jbrowserdriver.Util.Sync;
import com.machinepublishers.jbrowserdriver.config.UtilDynamic;

public class Element implements WebElement, JavascriptExecutor, FindsById, FindsByClassName,
    FindsByLinkText, FindsByName, FindsByCssSelector, FindsByTagName, FindsByXPath {
  private static final AtomicLong latestThread = new AtomicLong();
  private static final AtomicLong curThread = new AtomicLong();
  private static final Pattern rgb = Pattern.compile(
      "rgb\\(([0-9]{1,3}), ([0-9]{1,3}), ([0-9]{1,3})\\)");
  private final AtomicReference<UtilDynamic> node;
  private final AtomicReference<Robot> robot;
  private final AtomicReference<Timeouts> timeouts;
  private final boolean isWindow;
  private final long settingsId;

  Element(final AtomicReference<UtilDynamic> node, final AtomicReference<Robot> robot,
      final AtomicReference<Timeouts> timeouts, final long settingsId) {
    this.isWindow = node.get().is(Document.class);
    this.node = node;
    this.robot = robot;
    this.timeouts = timeouts;
    this.settingsId = settingsId;
  }

  static Element create(final AtomicReference<UtilDynamic> engine, final AtomicReference<Robot> robot,
      final AtomicReference<Timeouts> timeouts) {
    final long settingsId = Long.parseLong(engine.get().call("getUserAgent").toString());
    final AtomicReference<UtilDynamic> doc = new AtomicReference<UtilDynamic>(
        Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<UtilDynamic>() {
          @Override
          public UtilDynamic perform() {
            return engine.get().call("getDocument");
          }
        }, settingsId));
    return new Element(doc, robot, timeouts, settingsId);
  }

  @Override
  public void click() {
    Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
      @Override
      public Object perform() {
        node.get().call("scrollIntoView");
        UtilDynamic obj = node.get().call("getBoundingClientRect");
        double y = Double.parseDouble(obj.call("getMember", "top").toString());
        double x = Double.parseDouble(obj.call("getMember", "left").toString());
        robot.get().mouseMove(x, y);
        robot.get().mouseClick(MouseButton.LEFT);
        return null;
      }
    }, settingsId);
  }

  @Override
  public void submit() {
    Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
      @Override
      public Object perform() {
        if (node.get().is(HTMLInputElement.class)) {
          ((HTMLInputElement) node.get()).getForm().submit();
        } else if (node.get().is(HTMLFormElement.class)) {
          ((HTMLFormElement) node.get()).submit();
        }
        return null;
      }
    }, settingsId);
  }

  @Override
  public void sendKeys(final CharSequence... keys) {
    Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
      @Override
      public Object perform() {
        node.get().call("scrollIntoView");
        node.get().call("focus");
        robot.get().keysType(keys);
        return null;
      }
    }, settingsId);
  }

  @Override
  public void clear() {
    Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
      @Override
      public Object perform() {
        node.get().call("scrollIntoView");
        node.get().call("focus");
        node.get().call("setValue", "");
        return null;
      }
    }, settingsId);
  }

  @Override
  public String getAttribute(final String attrName) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<String>() {
      @Override
      public String perform() {
        String val = (String) (node.get().call("getMember", attrName).unwrap());
        return val == null || val.equals("undefined") ? "" : val;
      }
    }, settingsId);
  }

  @Override
  public String getCssValue(final String name) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<String>() {
      @Override
      public String perform() {
        return cleanUpCssVal((String) (node.get().call("eval", "var me = this;"
            + "(function(){"
            + "  return window.getComputedStyle(me).getPropertyValue('" + name + "');"
            + "})();").unwrap()));
      }
    }, settingsId);
  }

  private static String cleanUpCssVal(String rgbStr) {
    if (rgbStr != null) {
      Matcher matcher = rgb.matcher(rgbStr);
      if (matcher.matches()) {
        return "rgba(" + matcher.group(1) + ", "
            + matcher.group(2) + ", " + matcher.group(3) + ", 1)";
      }
    }
    return rgbStr == null ? "" : rgbStr;
  }

  @Override
  public Point getLocation() {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Point>() {
      @Override
      public Point perform() {
        UtilDynamic obj = node.get().call("getBoundingClientRect");
        int y = (int) Math.rint(Double.parseDouble(obj.call("getMember", "top").toString()));
        int x = (int) Math.rint(Double.parseDouble(obj.call("getMember", "left").toString()));
        return new Point(x, y);
      }
    }, settingsId);
  }

  @Override
  public Dimension getSize() {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Dimension>() {
      @Override
      public Dimension perform() {
        UtilDynamic obj = node.get().call("getBoundingClientRect");
        int y = (int) Math.rint(Double.parseDouble(obj.call("getMember", "top").toString()));
        int y2 = (int) Math.rint(Double.parseDouble(obj.call("getMember", "bottom").toString()));
        int x = (int) Math.rint(Double.parseDouble(obj.call("getMember", "left").toString()));
        int x2 = (int) Math.rint(Double.parseDouble(obj.call("getMember", "right").toString()));
        return new Dimension(x2 - x, y2 - y);
      }
    }, settingsId);
  }

  @Override
  public String getTagName() {
    return getAttribute("tagName");
  }

  @Override
  public String getText() {
    return getAttribute("textContent");
  }

  @Override
  public boolean isDisplayed() {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Boolean>() {
      @Override
      public Boolean perform() {
        UtilDynamic obj = node.get().call("getBoundingClientRect");
        int y = (int) Math.rint(Double.parseDouble(obj.call("getMember", "top").toString()));
        int y2 = (int) Math.rint(Double.parseDouble(obj.call("getMember", "bottom").toString()));
        int x = (int) Math.rint(Double.parseDouble(obj.call("getMember", "left").toString()));
        int x2 = (int) Math.rint(Double.parseDouble(obj.call("getMember", "right").toString()));
        return (Boolean)
        node.get().call("eval", "var me = this;"
            + "        (function(){"
            + "          for(var i = " + x + "; i < " + (x2 + 1) + "; i++){"
            + "            for(var j = " + y + "; j < " + (y2 + 1) + "; j++){"
            + "              if(document.elementFromPoint(i,j) == me){"
            + "                return true;"
            + "              }"
            + "            }"
            + "          }"
            + "          return false;})();").unwrap();
      }
    }, settingsId);
  }

  @Override
  public boolean isEnabled() {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Boolean>() {
      @Override
      public Boolean perform() {
        String val = node.get().call("getMember", "disabled").toString();
        return val == null || "undefined".equals(val) || val.isEmpty();
      }
    }, settingsId);
  }

  @Override
  public boolean isSelected() {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Boolean>() {
      @Override
      public Boolean perform() {
        String selected = node.get().call("getMember", "selected").toString();
        String checked = node.get().call("getMember", "checked").toString();
        return (selected != null && !"undefined".equals(selected) && !selected.isEmpty())
            || (checked != null && !"undefined".equals(checked) && !checked.isEmpty());
      }
    }, settingsId);
  }

  @Override
  public WebElement findElement(By by) {
    return by.findElement(this);
  }

  @Override
  public List<WebElement> findElements(By by) {
    return by.findElements(this);
  }

  @Override
  public WebElement findElementByXPath(final String expr) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<WebElement>() {
      @Override
      public WebElement perform() {
        try {
          final XPath xPath = XPathFactory.newInstance().newXPath();
          return new Element(
              new AtomicReference<UtilDynamic>(
                  new UtilDynamic(xPath.evaluate(expr, node.get(), XPathConstants.NODE))), robot, timeouts, settingsId);
        } catch (XPathExpressionException e) {
          throw new RuntimeException(e);
        }
      }
    }, settingsId);
  }

  @Override
  public List<WebElement> findElementsByXPath(final String expr) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<List<WebElement>>() {
      @Override
      public List<WebElement> perform() {
        try {
          XPath xPath = XPathFactory.newInstance().newXPath();
          NodeList list = (NodeList) xPath.evaluate(expr, node.get(), XPathConstants.NODESET);
          List<WebElement> elements = new ArrayList<WebElement>();
          for (int i = 0; i < list.getLength(); i++) {
            elements.add(new Element(new AtomicReference<UtilDynamic>(new UtilDynamic(list.item(i))), robot, timeouts, settingsId));
          }
          return elements;
        } catch (XPathExpressionException e) {
          throw new RuntimeException(e);
        }
      }
    }, settingsId);
  }

  @Override
  public WebElement findElementByTagName(String tagName) {
    List<WebElement> list = byTagName(tagName);
    return list.isEmpty() ? null : list.get(0);
  }

  @Override
  public List<WebElement> findElementsByTagName(String tagName) {
    return byTagName(tagName);
  }

  private List<WebElement> byTagName(final String tagName) {
    return (List<WebElement>) executeScript("return this.getElementsByTagName('" + tagName + "');");
  }

  @Override
  public WebElement findElementByCssSelector(final String expr) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<WebElement>() {
      @Override
      public WebElement perform() {
        UtilDynamic result = node.get().call("querySelector", expr);
        if (result == null) {
          return null;
        }
        return new Element(new AtomicReference<UtilDynamic>(result), robot, timeouts, settingsId);
      }
    }, settingsId);
  }

  @Override
  public List<WebElement> findElementsByCssSelector(final String expr) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<List<WebElement>>() {
      @Override
      public List<WebElement> perform() {
        List<WebElement> elements = new ArrayList<WebElement>();
        UtilDynamic result = node.get().call("querySelectorAll", expr);
        for (int i = 0;; i++) {
          UtilDynamic cur = result.call("getSlot", i);
          if (cur.is(Node.class)) {
            elements.add(new Element(new AtomicReference<UtilDynamic>(cur), robot, timeouts, settingsId));
          } else {
            break;
          }
        }
        return elements;
      }
    }, settingsId);
  }

  @Override
  public WebElement findElementByName(String name) {
    return findElementByCssSelector("*[name='" + name + "']");
  }

  @Override
  public List<WebElement> findElementsByName(String name) {
    return findElementsByCssSelector("*[name='" + name + "']");
  }

  @Override
  public WebElement findElementByLinkText(final String text) {
    List<WebElement> list = byLinkText(text, false, false);
    return list.isEmpty() ? null : list.get(0);
  }

  @Override
  public WebElement findElementByPartialLinkText(String text) {
    List<WebElement> list = byLinkText(text, false, true);
    return list.isEmpty() ? null : list.get(0);
  }

  @Override
  public List<WebElement> findElementsByLinkText(String text) {
    return byLinkText(text, true, false);
  }

  @Override
  public List<WebElement> findElementsByPartialLinkText(String text) {
    return byLinkText(text, true, true);
  }

  private List<WebElement> byLinkText(final String text,
      final boolean multiple, final boolean partial) {
    return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<List<WebElement>>() {
      @Override
      public List<WebElement> perform() {
        List<WebElement> nodes = (List<WebElement>) findElementsByTagName("a");
        List<WebElement> elements = new ArrayList<WebElement>();
        for (WebElement cur : nodes) {
          if ((partial && cur.getText().contains(text))
              || (!partial && cur.getText().equals(text))) {
            elements.add(cur);
            if (!multiple) {
              break;
            }
          }
        }
        return elements;
      }
    }, settingsId);
  }

  @Override
  public WebElement findElementByClassName(String cssClass) {
    List<WebElement> list = byCssClass(cssClass);
    return list.isEmpty() ? null : list.get(0);
  }

  @Override
  public List<WebElement> findElementsByClassName(String cssClass) {
    return byCssClass(cssClass);
  }

  private List<WebElement> byCssClass(String cssClass) {
    return (List<WebElement>) executeScript("return this.getElementsByClassName('" + cssClass + "');");
  }

  @Override
  public WebElement findElementById(final String id) {
    return findElementByCssSelector("*[id='" + id + "']");
  }

  @Override
  public List<WebElement> findElementsById(String id) {
    return findElementsByCssSelector("*[id='" + id + "']");
  }

  @Override
  public Object executeAsyncScript(final String script, final Object... args) {
    lock();
    try {
      Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
        @Override
        public Object perform() {
          return script(true, script, args);
        }
      }, settingsId);
      int sleep = 1;
      final int sleepBackoff = 2;
      final int sleepMax = 500;
      while (true) {
        sleep = sleep < sleepMax ? sleep * sleepBackoff : sleep;
        try {
          Thread.sleep(sleep);
        } catch (InterruptedException e) {}
        UtilDynamic result = Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<UtilDynamic>() {
          @Override
          public UtilDynamic perform() {
            return node.get().call("eval", "(function(){return this.screenslicerCallbackVal;})();");
          }
        }, settingsId);
        if (!result.is(String.class) || !"undefined".equals(result)) {
          result = new UtilDynamic(parseScriptResult(result));
          if (result.is(List.class)) {
            if (((List) result).size() == 0) {
              return null;
            }
            if (((List) result).size() == 1) {
              return ((List) result.unwrap()).get(0);
            }
          }
          return result.unwrap();
        }
      }
    } finally {
      unlock();
    }
  }

  @Override
  public Object executeScript(final String script, final Object... args) {
    lock();
    try {
      return Util.exec(timeouts.get().getScriptTimeoutMS(), new Sync<Object>() {
        @Override
        public Object perform() {
          return script(false, script, args);
        }
      }, settingsId);
    } finally {
      unlock();
    }
  }

  private void lock() {
    long myThread = latestThread.incrementAndGet();
    synchronized (curThread) {
      while (myThread != curThread.get() + 1) {
        try {
          curThread.wait();
        } catch (Exception e) {
          Logs.exception(e);
        }
      }
    }
  }

  private void unlock() {
    curThread.incrementAndGet();
    synchronized (curThread) {
      curThread.notifyAll();
    }
  }

  private Object script(boolean callback, String script, Object[] args) {
    args = args == null ? new Object[0] : args;
    if (callback) {
      Object[] tmp = new Object[args.length + 1];
      for (int i = 0; i < args.length; i++) {
        tmp[i] = args[i];
      }
      args = tmp;
      this.node.get().call("eval", "(function(){"
          + "          this.screenslicerCallback = function(){"
          + "            this.screenslicerCallbackVal = arguments;"
          + "          }"
          + "        }).apply(this);"
          + "        this.screenslicerJS = function(){"
          + (isWindow ? "var window = this;" : "")
          + "          arguments[arguments.length-1] = this.screenslicerCallback;"
          + "          return (function(){" + script + "}).apply(this, arguments);"
          + "        };");
    } else {
      this.node.get().call("eval", "this.screenslicerJS = function(){"
          + (isWindow ? "var window = this;" : "")
          + "          return (function(){" + script + "}).apply(this, arguments);"
          + "        };");
    }
    return parseScriptResult(this.node.get().call("screenslicerJS", args));
  }

  private Object parseScriptResult(UtilDynamic obj) {
    if (obj == null || (obj.is(String.class) && "undefined".equals(obj))) {
      return null;
    }
    if (obj.is(Node.class)) {
      return new Element(new AtomicReference<UtilDynamic>(obj), robot, timeouts, settingsId);
    }
    if (obj.unwrap().getClass().getName().equals(JSObject.class.getName())) {
      List<Object> result = new ArrayList<Object>();
      for (int i = 0;; i++) {
        UtilDynamic cur = obj.call("getSlot", i);
        if (cur.is(String.class) && "undefined".equals(cur)) {
          break;
        }
        result.add(parseScriptResult(cur));
      }
      return result;
    }
    if (obj.is(Boolean.class)) {
      return obj.unwrap();
    }
    if (obj.is(Long.class)) {
      return obj.unwrap();
    }
    if (obj.is(Integer.class)) {
      return new Long((Integer) obj.unwrap());
    }
    if (obj.is(Double.class)) {
      return obj.unwrap();
    }
    if (obj.is(Float.class)) {
      return new Double((Double) obj.unwrap());
    }
    return obj.toString();
  }
}
