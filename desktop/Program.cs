using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace WindTunnelLab
{
    /// <summary>
    /// 桌面壳：用 WebView2 显示嵌在 exe 里的 index.html。
    ///
    /// 为什么这么做：
    ///   整套模拟是 Canvas 2D + JS。用 Electron 要 100+ MB，用 CEF 更重；
    ///   WebView2 用的是系统自带运行时（Win10/11 装过 Edge 就有），
    ///   exe 只多几 MB，行为还和 APK 那边一致。
    ///
    /// 关键点：
    ///   1. 网页以「嵌入资源」打进 exe，首次运行解压到 %LOCALAPPDATA%\WindTunnelLab\web
    ///      —— 单文件分发，不依赖旁边放一堆文件
    ///   2. 必须显式设置 UserDataFolder：默认会写到 exe 同目录，放到只读目录就会崩
    ///   3. 用 file:// 加载，页面里的 ?img= 相对路径还能用
    ///   4. 崩了要能看到原因：全局异常直接弹窗，不要静默退出
    /// </summary>
    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Application.ThreadException += (s, e) => ShowFatal(e.Exception);
            AppDomain.CurrentDomain.UnhandledException += (s, e) => ShowFatal(e.ExceptionObject as Exception);

            string webDir;
            try
            {
                webDir = ExtractWeb();
            }
            catch (Exception ex)
            {
                ShowFatal(ex);
                return;
            }

            Application.Run(new MainForm(webDir));
        }

        /// <summary>把嵌入的网页资源解压到用户目录（每次启动覆盖，保证和 exe 同版本）</summary>
        private static string ExtractWeb()
        {
            string dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "WindTunnelLab", "web");
            Directory.CreateDirectory(dir);

            Assembly asm = Assembly.GetExecutingAssembly();
            var map = new (string Resource, string File)[]
            {
                ("web.index.html", "index.html"),
                ("web.test-tank.png", "test-tank.png"),
                ("web.v1-lbm.html", "v1-lbm.html"),
            };

            foreach (var item in map)
            {
                using (Stream src = asm.GetManifestResourceStream(item.Resource))
                {
                    if (src == null) continue;   // 某个资源没打进来就跳过，页面还能跑
                    string dst = Path.Combine(dir, item.File);
                    using (FileStream fs = File.Create(dst))
                    {
                        src.CopyTo(fs);
                    }
                }
            }

            return dir;
        }

        private static void ShowFatal(Exception ex)
        {
            string msg = ex == null ? "未知错误" : ex.ToString();
            try
            {
                MessageBox.Show(msg, "风洞实验室 · 启动失败",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            catch
            {
                // 连弹窗都不行就放弃
            }
        }
    }

    internal sealed class MainForm : Form
    {
        private readonly string _webDir;
        private WebView2 _web;

        public MainForm(string webDir)
        {
            _webDir = webDir;

            Text = "风洞实验室 · Wind Tunnel Laboratory";
            Width = 1280;
            Height = 800;
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(720, 480);
            BackColor = Color.FromArgb(5, 7, 13);
            KeyPreview = true;

            _web = new WebView2 { Dock = DockStyle.Fill };
            Controls.Add(_web);

            Load += async (s, e) => await InitWebViewAsync();
        }

        private async System.Threading.Tasks.Task InitWebViewAsync()
        {
            try
            {
                // UserDataFolder 必须显式给：默认在 exe 旁边，放 Program Files 会没权限
                string userData = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "WindTunnelLab", "webview2");
                Directory.CreateDirectory(userData);

                CoreWebView2Environment env =
                    await CoreWebView2Environment.CreateAsync(null, userData);
                await _web.EnsureCoreWebView2Async(env);

                CoreWebView2Settings set = _web.CoreWebView2.Settings;
                set.AreDefaultContextMenusEnabled = true;
                set.IsStatusBarEnabled = false;
                set.AreDevToolsEnabled = true;          // 出问题能按 F12 看
                set.IsZoomControlEnabled = true;

                // 允许拖拽文件进来（页面本身支持拖透明 PNG）
                _web.AllowExternalDrop = true;

                string index = Path.Combine(_webDir, "index.html");
                _web.CoreWebView2.Navigate(new Uri(index).AbsoluteUri);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "WebView2 初始化失败：\n\n" + ex.Message +
                    "\n\n如果系统没装 WebView2 运行时，去这里下载：\n" +
                    "https://developer.microsoft.com/microsoft-edge/webview2/",
                    "风洞实验室", MessageBoxButtons.OK, MessageBoxIcon.Error);
                Close();
            }
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (e.KeyCode == Keys.F11)   // F11 全屏
            {
                FormBorderStyle = FormBorderStyle == FormBorderStyle.None
                    ? FormBorderStyle.Sizable
                    : FormBorderStyle.None;
                WindowState = FormBorderStyle == FormBorderStyle.None
                    ? FormWindowState.Maximized
                    : FormWindowState.Normal;
            }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            try { _web?.Dispose(); } catch { }
            base.OnFormClosing(e);
        }
    }
}
