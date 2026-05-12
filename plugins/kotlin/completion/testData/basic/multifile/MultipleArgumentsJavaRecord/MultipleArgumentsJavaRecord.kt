fun test(email: String, password: String, recoveryPassword: String, flags: Int) {
    JavaRecord(<caret>)
}

// EXIST:  { "itemText": "email, password, flags" }
// IGNORE_K1