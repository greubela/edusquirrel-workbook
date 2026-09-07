function setupFileInspectorWidget(source) {
  const t = source || getWidgetLang("fileInspector");
  var container = document.getElementById("widget-s3-fileinspector");
  if (!container) { return; }

  container.classList.remove("widget-placeholder");
  container.style.cssText = "border:1px solid var(--border);background:#fffdf8;padding:0.8rem 1rem 1rem;border-radius:3px;margin:0.75rem 0";

  container.innerHTML =
    '<p style="font-size:12px;font-weight:600;color:var(--muted);margin:0 0 10px;text-transform:uppercase;letter-spacing:0.04em">' +
      t.visualLabel +
    '</p>' +
    '<div style="display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-bottom:18px">' +

      '<div style="border:1px solid var(--border);border-radius:4px;overflow:hidden;background:#fff">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 12px 8px;background:var(--bg);border-bottom:1px solid var(--border)">' +
          '<div style="display:flex;align-items:center;gap:8px">' +
            '<span style="font-size:16px">\uD83D\uDCC4</span>' +
            '<span style="font-weight:600;font-family:monospace;font-size:14px">' + t.txtFilename + '</span>' +
          '</div>' +
          '<span style="font-size:12px;color:var(--muted);font-family:monospace">' + t.txtSize + '</span>' +
        '</div>' +
        '<div style="padding:14px 16px;min-height:140px">' +
          '<div style="font-family:Georgia,serif;font-size:14px;line-height:1.65;color:#1f2328;white-space:pre-wrap">' +
            t.txtContent +
          '</div>' +
        '</div>' +
      '</div>' +

      '<div style="border:1px solid var(--border);border-radius:4px;overflow:hidden;background:#fff">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 12px 8px;background:var(--bg);border-bottom:1px solid var(--border)">' +
          '<div style="display:flex;align-items:center;gap:8px">' +
            '<span style="font-size:16px">\uD83D\uDCC4</span>' +
            '<span style="font-weight:600;font-family:monospace;font-size:14px">' + t.docxFilename + '</span>' +
          '</div>' +
          '<span style="font-size:12px;color:var(--muted);font-family:monospace">' + t.docxSize + '</span>' +
        '</div>' +
        '<div style="padding:18px 22px;min-height:140px;background:#fafafa;border-bottom:1px solid var(--border)">' +
          '<div style="font-family:Calibri,Arial,sans-serif;font-size:14px;line-height:1.5;color:#1f2328">' +
            '<p style="margin:0 0 10px">Sehr geehrte Damen und Herren,</p>' +
            '<p style="margin:0 0 10px">hiermit möchten wir uns für die <strong>gute Zusammenarbeit</strong> bedanken.</p>' +
            '<p style="margin:16px 0 0">Mit freundlichen Grüßen<br><em>Ihr Muster-Team</em></p>' +
          '</div>' +
        '</div>' +
        '<details style="padding:0">' +
          '<summary style="padding:8px 12px;font-size:12px;color:var(--accent);cursor:pointer;background:var(--bg)">' +
            t.structureLabel +
          '</summary>' +
          '<div style="padding:10px 12px;font-family:monospace;font-size:13px;line-height:1.7">' +

            '<div style="margin:0">' +
              '<div style="padding:2px 4px;display:flex;align-items:center;gap:4px;font-size:13px">' +
                '<span style="color:var(--muted)">\u251C\u2500</span> ' +
                '<span style="color:#8b6f47">[Content_Types].xml</span>' +
                '<span style="color:var(--muted);font-size:11px;margin-left:8px">' + t.contentTypesDesc + '</span>' +
              '</div>' +
            '</div>' +

            '<div style="margin:0">' +
              '<div style="padding:2px 4px;display:flex;align-items:center;gap:4px;font-size:13px">' +
                '<span style="color:var(--muted)">\u251C\u2500</span> ' +
                '<span style="color:#8b6f47">docProps/</span>' +
              '</div>' +
              '<div style="padding:4px 0 4px 20px;border-left:1px dashed var(--border);margin-left:6px;color:#555;font-size:12px">' +
                'core.xml <span style="color:var(--muted)">(' + t.coreDesc + ')</span><br>' +
                'app.xml <span style="color:var(--muted)">(' + t.appDesc + ')</span>' +
              '</div>' +
            '</div>' +

            '<div style="margin:0">' +
              '<div style="padding:2px 4px;display:flex;align-items:center;gap:4px;font-size:13px">' +
                '<span style="color:var(--muted)">\u2514\u2500</span> ' +
                '<span style="color:#8b6f47;font-weight:600">word/document.xml</span>' +
                '<span style="color:var(--muted);font-size:11px;margin-left:8px">' + t.documentDesc + '</span>' +
              '</div>' +
            '</div>' +

          '</div>' +
        '</details>' +
      '</div>' +

    '</div>';
}
