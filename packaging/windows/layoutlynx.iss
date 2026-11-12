; Inno Setup script for LayoutLynx.
;
; Packages the jpackage app-image (directory "LayoutLynx" in the repo root) into a
; single installer that supports BOTH install scopes:
;   - "Install for all users"      -> admin, C:\Program Files\LayoutLynx, HKLM
;   - "Install for me only"        -> no admin, %LOCALAPPDATA%\Programs\LayoutLynx, HKCU
;
; The user is prompted at startup. Automation (e.g. the Glowing Cat installer) can force a
; mode silently:
;   LayoutLynx-Setup.exe /VERYSILENT /ALLUSERS       (all users, triggers UAC)
;   LayoutLynx-Setup.exe /VERYSILENT /CURRENTUSER    (current user, no elevation)
;
; Build (from repo root, after jpackage --type app-image created .\LayoutLynx):
;   ISCC /DAppVersion=1.4.0 /DArch=x64 packaging\windows\layoutlynx.iss
;   (Arch is "x64" or "arm64"). Output: LayoutLynx-Setup-<ver>-<arch>.exe in the repo root.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef Arch
  #define Arch "x64"
#endif

#define AppName "LayoutLynx"
#define AppExe "LayoutLynx.exe"
#define RepoRoot AddBackslash(SourcePath) + "..\.."

[Setup]
; Stable AppId so upgrades and uninstalls find the previous install. Never change it.
AppId={{6F3B2C1E-8A47-4D5B-9E20-4C7A1D93B5F8}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=Glowing Cat
AppCopyright=(C) 2026 Glowing Cat
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
SetupIconFile={#RepoRoot}\app\src\main\resources\app_icon.ico
LicenseFile={#RepoRoot}\LICENSE
OutputDir={#RepoRoot}
OutputBaseFilename={#AppName}-Setup-{#AppVersion}-{#Arch}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

; Default to per-user (no UAC); let the user switch to all-users on the first dialog, or
; force either mode with /ALLUSERS or /CURRENTUSER on the command line.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog commandline

#if Arch == "arm64"
ArchitecturesAllowed=arm64
ArchitecturesInstallIn64BitMode=arm64
#else
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
#endif

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#RepoRoot}\{#AppName}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; {group} and {autodesktop} resolve to the all-users or current-user location automatically.
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent
