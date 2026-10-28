use erezcraft_core::{install::{Installer,hash_reader},manifest::{Config,Manifest,PackFile,Policy}};
use std::path::PathBuf;
#[tokio::main]
async fn main()->Result<(),Box<dyn std::error::Error>>{
 let root=PathBuf::from("D:/repos/Blender/creations/17_rift_engineer_skin");
 let instance=root.join("reports/launcher-fixture-S03-final");assert!(!instance.exists(),"Fresh owned fixture required");
 let config:Config=serde_json::from_slice(&std::fs::read(root.join("exports/delivery/launcher-config.public.json"))?)?;config.validate()?;
 let full:Manifest=serde_json::from_slice(&std::fs::read(root.join("exports/delivery/client-manifest.UNSIGNED.json"))?)?;full.validate(&config)?;
 let cpm:PackFile=serde_json::from_slice(&std::fs::read(root.join("exports/delivery/cpm-file.json"))?)?;assert_eq!(cpm.policy,Policy::Managed);
 let guard=full.files.iter().find(|f|f.path.starts_with("mods/erez-avatar-compat-")).unwrap().clone();
 let mut fixture=full.clone();fixture.files=vec![cpm.clone(),guard.clone()];fixture.release_id="owned-cpm-installer-fixture".into();
 let installer=Installer::new(&instance)?;
 std::fs::create_dir_all(instance.join(".erezcraft/cache"))?;
 let guard_bytes=std::fs::read(root.join("exports/erez-avatar-compat-0.1.0.jar"))?;assert_eq!(hash_reader(guard_bytes.as_slice())?,guard.sha256);
 std::fs::write(instance.join(".erezcraft/cache").join(&guard.sha256),guard_bytes)?;
 std::fs::create_dir_all(instance.join("mods"))?;std::fs::write(instance.join("mods/unlisted-fixture.jar"),b"fixture only")?;
 std::fs::create_dir_all(instance.join("player_models"))?;std::fs::write(instance.join("player_models/personal.cpmmodel"),b"personal model fixture")?;
 std::fs::write(instance.join("options.txt"),b"personal controls fixture")?;
 let hash="a".repeat(64);let first=installer.synchronize(&config,&fixture,&hash,|_|{}).await?;
 assert_eq!(first.repaired,2);assert_eq!(first.quarantined,1);
 let file=instance.join(&cpm.path);assert_eq!(hash_reader(std::fs::File::open(&file)?)?,cpm.sha256);
 let repeat=installer.synchronize(&config,&fixture,&hash,|_|{}).await?;assert_eq!(repeat.repaired,0);assert_eq!(repeat.quarantined,0);assert!(file.exists());
 assert_eq!(hash_reader(std::fs::File::open(instance.join(&guard.path))?)?,guard.sha256);
 let mut bytes=std::fs::read(&file)?;bytes[120]^=1;std::fs::write(&file,&bytes)?;
 let repaired=installer.synchronize(&config,&fixture,&hash,|_|{}).await?;assert_eq!(repaired.repaired,1);assert_eq!(hash_reader(std::fs::File::open(&file)?)?,cpm.sha256);
 assert_eq!(std::fs::read(instance.join("player_models/personal.cpmmodel"))?,b"personal model fixture");
 assert_eq!(std::fs::read(instance.join("options.txt"))?,b"personal controls fixture");
 let proof=serde_json::json!({"passed":true,"real_launcher_installer":true,"real_https_cpm_download":true,"manifest_schema_validated":true,"full_candidate_files":full.files.len(),
 "focused_installer_fixture_files":2,"compat_jar_from_verified_local_cache":true,"first_repaired":first.repaired,"unlisted_fixture_mod_quarantined":first.quarantined,"repeat_repaired":repeat.repaired,"repeat_quarantined":repeat.quarantined,
 "corruption_repaired":repaired.repaired,"personal_model_and_options_preserved":true,"live_instance_used":false,"published":false,"production_signature_created":false,
 "limitation":"Installer behavior tested with a two-mod fixture; CPM fetched over HTTPS, owned compatibility JAR supplied via verified local cache. The full unsigned candidate is schema-validated but is not a published or fully installed pack release."});
 std::fs::write(root.join("reports/launcher_installer.json"),serde_json::to_vec_pretty(&proof)?)?;println!("{proof}");Ok(())
}
