// ------------------------------------------------------------------------------
//  <autogenerated>
//      This code was generated by a tool.
//      Mono Runtime Version: 4.0.30319.17020
// 
//      Changes to this file may cause incorrect behavior and will be lost if 
//      the code is regenerated.
//  </autogenerated>
// ------------------------------------------------------------------------------

// 
// This source code was auto-generated by Web Services Description Language Utility
//Mono Framework v4.0.30319.17020
//


/// <remarks/>
[System.Web.Services.WebServiceBinding(Name="EventLoggerPort", Namespace="ATSAdvanced.DTO")]
[System.Diagnostics.DebuggerStepThroughAttribute()]
[System.ComponentModel.DesignerCategoryAttribute("code")]
public partial class EventLoggerService : System.Web.Services.Protocols.SoapHttpClientProtocol {
    
    private System.Threading.SendOrPostCallback logMessageOperationCompleted;
    
	public EventLoggerService(string url) {
//        this.Url = "http://127.0.0.1:9999/eventlogger";
		this.Url = url;
    }
    
    public event logMessageCompletedEventHandler logMessageCompleted;
    
    [System.Web.Services.Protocols.SoapDocumentMethodAttribute("", RequestNamespace="ATSAdvanced.DTO", ResponseNamespace="ATSAdvanced.DTO", ParameterStyle=System.Web.Services.Protocols.SoapParameterStyle.Wrapped, Use=System.Web.Services.Description.SoapBindingUse.Literal)]
    [return: System.Xml.Serialization.XmlElementAttribute("reponse")]
    public ProgramSendMessage logMessage(ProgramSendMessage message) {
        object[] results = this.Invoke("logMessage", new object[] {
                    message});
        return ((ProgramSendMessage)(results[0]));
    }
    
    public System.IAsyncResult BeginlogMessage(ProgramSendMessage message, System.AsyncCallback callback, object asyncState) {
        return this.BeginInvoke("logMessage", new object[] {
                    message}, callback, asyncState);
    }
    
    public ProgramSendMessage EndlogMessage(System.IAsyncResult asyncResult) {
        object[] results = this.EndInvoke(asyncResult);
        return ((ProgramSendMessage)(results[0]));
    }
    
    public void logMessageAsync(ProgramSendMessage message) {
        this.logMessageAsync(message, null);
    }
    
    public void logMessageAsync(ProgramSendMessage message, object userState) {
        if ((this.logMessageOperationCompleted == null)) {
            this.logMessageOperationCompleted = new System.Threading.SendOrPostCallback(this.OnlogMessageCompleted);
        }
        this.InvokeAsync("logMessage", new object[] {
                    message}, this.logMessageOperationCompleted, userState);
    }
    
    private void OnlogMessageCompleted(object arg) {
        if ((this.logMessageCompleted != null)) {
            System.Web.Services.Protocols.InvokeCompletedEventArgs invokeArgs = ((System.Web.Services.Protocols.InvokeCompletedEventArgs)(arg));
            this.logMessageCompleted(this, new logMessageCompletedEventArgs(invokeArgs.Results, invokeArgs.Error, invokeArgs.Cancelled, invokeArgs.UserState));
        }
    }
}

/// <remarks/>
[System.CodeDom.Compiler.GeneratedCodeAttribute("System.Xml", "4.0.30319.17020")]
[System.SerializableAttribute()]
[System.Diagnostics.DebuggerStepThroughAttribute()]
[System.ComponentModel.DesignerCategoryAttribute("code")]
[System.Xml.Serialization.XmlTypeAttribute("Program.SendMessage", Namespace="ATSAdvanced.DTO")]
public partial class ProgramSendMessage {
    
    private ProgramProperty[] propertiesField;
    
    private string nameField;
    
    /// <remarks/>
    [System.Xml.Serialization.XmlArray(IsNullable=true)]
    [System.Xml.Serialization.XmlArrayItem(Namespace="http://schemas.datacontract.org/2004/07/AtsAdvancedTest")]
    public ProgramProperty[] Properties {
        get {
            return this.propertiesField;
        }
        set {
            this.propertiesField = value;
        }
    }
    
    /// <remarks/>
    [System.Xml.Serialization.XmlElementAttribute(IsNullable=true)]
    public string name {
        get {
            return this.nameField;
        }
        set {
            this.nameField = value;
        }
    }
}

/// <remarks/>
[System.CodeDom.Compiler.GeneratedCodeAttribute("System.Xml", "4.0.30319.17020")]
[System.SerializableAttribute()]
[System.Diagnostics.DebuggerStepThroughAttribute()]
[System.ComponentModel.DesignerCategoryAttribute("code")]
[System.Xml.Serialization.XmlTypeAttribute("Program.Property", Namespace="http://schemas.datacontract.org/2004/07/AtsAdvancedTest")]
public partial class ProgramProperty {
    
    private string idField;
    
    private int indexField;
    
    private object valueField;
    
    /// <remarks/>
    [System.Xml.Serialization.XmlElementAttribute(IsNullable=true)]
    public string id {
        get {
            return this.idField;
        }
        set {
            this.idField = value;
        }
    }
    
    /// <remarks/>
    public int index {
        get {
            return this.indexField;
        }
        set {
            this.indexField = value;
        }
    }
    
    /// <remarks/>
    [System.Xml.Serialization.XmlElementAttribute(IsNullable=true)]
    public object value {
        get {
            return this.valueField;
        }
        set {
            this.valueField = value;
        }
    }
}

public partial class logMessageCompletedEventArgs : System.ComponentModel.AsyncCompletedEventArgs {
    
    private object[] results;
    
    internal logMessageCompletedEventArgs(object[] results, System.Exception exception, bool cancelled, object userState) : 
            base(exception, cancelled, userState) {
        this.results = results;
    }
    
    public ProgramSendMessage Result {
        get {
            this.RaiseExceptionIfNecessary();
            return ((ProgramSendMessage)(this.results[0]));
        }
    }
}

public delegate void logMessageCompletedEventHandler(object sender, logMessageCompletedEventArgs args);
