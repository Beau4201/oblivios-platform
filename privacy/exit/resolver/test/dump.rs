// SPDX-License-Identifier: Apache-2.0
// The daemon's reader and matcher, for differential.py: `masklist.rs` of oblivios-services/exit-daemon is included
// as it is (it uses std only).
//   dump LIST NAMES    one line per name: 1 when `classify` gives a company, else 0
//   accept LIST        `ok` when `MaskList::load` accepts the file, else `err`
#[path = "masklist.rs"]
mod masklist;

use masklist::MaskList;
use std::io::Read;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let list_path = std::path::Path::new(&args[2]);
    match args[1].as_str() {
        "accept" => println!("{}", if MaskList::load(list_path).is_ok() { "ok" } else { "err" }),
        "dump" => {
            let list = MaskList::load(list_path).expect("the list loads");
            let mut names = String::new();
            std::fs::File::open(&args[3]).unwrap().read_to_string(&mut names).unwrap();
            // The file ends with one newline; an empty line in it is the empty name.
            for name in names.strip_suffix('\n').unwrap_or(&names).split('\n') {
                println!("{}", u8::from(list.classify(name).is_some()));
            }
        }
        _ => panic!("mode"),
    }
}
