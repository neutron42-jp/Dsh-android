package ai.deepseek.dsh

/**
 * Built-in userscript: appends "App settings" and "Reload" rows to the bottom
 * of the DSH settings dialog (General pane), cloning DSH's own
 * "Keyboard shortcuts" row so the widgets inherit the live theme.
 *
 * Rows are injected only when the Android bridge (`window.DshApp`) exists, so
 * the same server keeps rendering an untouched UI in a desktop browser.
 * Lookups are structural or text-based: DSH ships hashed CSS-module class names
 * that change between builds.
 */
object BuiltInScript {
    val JS = """
    (function(){
      if (window.__dshAppInject) return;
      window.__dshAppInject = true;
      var HOLDER = 'dsh-app-rows';

      function bridge(){ return window.DshApp; }

      function findDialog(){
        var ds = document.querySelectorAll('[role="dialog"]');
        for (var i = 0; i < ds.length; i++) {
          if (ds[i].querySelector('nav') && /Settings/.test(ds[i].textContent || '')) return ds[i];
        }
        return null;
      }

      /** Row layout: children[0] is the text block (title, description), children[1] the button. */
      function makeRow(tpl, title, desc, label, onClick){
        var row = tpl.cloneNode(true);
        var text = row.children[0];
        if (text && text.children[0]) text.children[0].textContent = title;
        if (text && text.children[1]) text.children[1].textContent = desc;
        var btn = row.querySelector('button');
        if (btn) {
          btn.textContent = label;
          btn.setAttribute('aria-label', label);
          btn.removeAttribute('aria-keyshortcuts');
          btn.addEventListener('click', onClick);
        }
        return row;
      }

      function build(){
        if (!bridge()) return;
        var old = document.getElementById(HOLDER);
        if (old && old.isConnected) return;
        var dlg = findDialog();
        if (!dlg) return;
        var section = dlg.querySelector('[data-slot="settings.section"]');
        var inner = section && section.firstElementChild;
        if (!inner) return;
        var item = inner.querySelector('[data-slot="settings.general.item"]');
        var rows = (item || inner).children;
        var tpl = null;
        for (var i = 0; i < rows.length; i++) {
          if (/Keyboard shortcuts/.test(rows[i].textContent || '')) { tpl = rows[i]; break; }
        }
        if (!tpl) return;
        var holder = document.createElement('div');
        holder.id = HOLDER;
        holder.style.marginTop = '4px';
        holder.style.paddingTop = '8px';
        holder.style.borderTop = '1px solid rgba(128,128,128,0.25)';
        holder.appendChild(makeRow(tpl, 'App settings', 'Server, login, appearance', 'Open', function(){
          if (bridge()) DshApp.openSettings();
        }));
        holder.appendChild(makeRow(tpl, 'Reload', 'Restart the Web UI', 'Reload', function(){
          if (bridge()) DshApp.reload();
        }));
        inner.appendChild(holder);
      }

      // React re-creates the dialog on every open, so re-check on DOM changes.
      new MutationObserver(build).observe(document.documentElement, { childList: true, subtree: true });
      build();
    })();
    """.trimIndent()
}
